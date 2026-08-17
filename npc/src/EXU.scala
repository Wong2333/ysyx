package npc

import chisel3._
import chisel3.util._
import npc.EXUs._
import npc.PMU._

object JumpOp extends ChiselEnum{
    val none, jump = Value
}

object MemOp extends ChiselEnum{
    val none, load, store = Value
}

object MemSize extends ChiselEnum{
    val byte, half, word = Value
}

object WBSrc extends ChiselEnum{
    val none, alu, mem, pc_plus4, csr = Value
}

object BranchOp extends ChiselEnum{
    val none, beq, bne, blt, bge, bltu, bgeu = Value
}

object CSROp extends ChiselEnum{
    val none, csrrw, csrrs = Value
}

object SysOp extends ChiselEnum{
    val none, ecall, ebreak, mret = Value
}

class EXUCTLBundle extends Bundle{
    val ALU_Src1    = Output(ALUSrc1())     // rs1 / pc
    val ALU_Src2    = Output(ALUSrc2())     // rs2 / imm / shamt / csr
    val ALU_Control = Output(ALUOp())       // ALU 运算
    val reg_write   = Output(Bool())        // 是否写寄存器（译码唯一来源）
    val wb_src      = Output(WBSrc())       // 写回数据源
    val mem_op      = Output(MemOp())       // none / load / store
    val mem_size    = Output(MemSize())     // byte / half / word
    val mem_signed  = Output(Bool())        // 符号扩展（仅 load 有效）
    val branch_op   = Output(BranchOp())    // 条件分支
    val jump_op     = Output(JumpOp())      // 无条件跳转
    val csr_op      = Output(CSROp())       // CSR 读写
    val sys_op      = Output(SysOp())       // ecall / ebreak / mret
    val fence_i     = Output(Bool())
}

class ysyx_26030092_EXU extends Module{
    // ========== IO ==========
    // 控制冒险：分支/跳转/异常判定点（接受拍），flush 与重定向目标从这里直接发出
    val flush = IO(Output(Bool()))
    val redirectPC = IO(Output(UInt(32.W)))
    val mtvec = IO(Input(UInt(32.W)))

    val idu_out = IO(Flipped(Decoupled(new IDUBundle)))
    val wbu_in = IO(Decoupled(new WriteBackPortBundle))

    val lsu_in = IO(Decoupled(new LSUBundle))
    val lsu_out = IO(Flipped(Decoupled(new Bundle {
        val rdata = Input(UInt(32.W))
        val error = Input(Bool())
    })))

    // 提交指令信息
    val commit_info = IO(Output(new CommitInfoBundle))

    // RAW 冒险检测信息（EX 层恒不命中：非访存指令的写回窗口由 wb_reg/ex_pending 覆盖）
    val ex_wb_info  = IO(Output(new StageWriteInfoBundle))
    val mem_wb_info = IO(Output(new StageWriteInfoBundle))
    val ex_csr_info = IO(Output(new CSRWriteInfoBundle))

    // ========== 状态机：接受拍 = 执行拍 ==========
    // s_idle: 接受新指令，当拍组合执行
    //   - 非访存且 LSU 空闲（无更老访存）→ 结果锁存进 wb_reg（最终写回）
    //   - 非访存且 LSU 忙（有更老访存在途）→ 结果锁存进 ex_pending 等待，进入 s_wait_lsu_valid
    //   - 访存 → 发 LSU + 保存信息，进入 s_wait_lsu_valid
    // s_wait_lsu_valid: 等 LSU 返回（其内部已锁存），返回拍直接锁存进 wb_reg
    val s_idle :: s_wait_lsu_valid :: Nil = Enum(2)
    val state = RegInit(s_idle)

    // 访存等待期保存的信息
    val wb_rd      = Reg(UInt(5.W))      // 写回地址
    val wb_regwr   = RegInit(false.B)    // load=true / store=false
    val wb_pc      = Reg(UInt(32.W))     // commit_info.pc
    val wb_memaddr = Reg(UInt(32.W))     // commit_info.mem_addr

    // 最终写回槽：所有写回结果统一锁存这里，wbu_in 由它驱动
    val wb_reg = Reg(new WriteBackPortBundle)
    val wb_reg_valid = RegInit(false.B)
    val wb_commit = Reg(new CommitInfoBundle)

    // 非访存暂存槽：LSU 忙时的结果，等 wb_reg 排空后接力写回（保证按序）
    val ex_pending = Reg(new WriteBackPortBundle)
    val ex_pending_valid = RegInit(false.B)
    val ex_pending_commit = Reg(new CommitInfoBundle)

    idu_out.ready := false.B
    lsu_out.ready := false.B
    lsu_in.valid := false.B
    wbu_in.valid := false.B

    // 接受拍判定（基于输入侧 idu_out.bits，接受拍即执行拍）
    val is_mem  = idu_out.bits.exu_ctl.mem_op =/= MemOp.none
    val is_load = idu_out.bits.exu_ctl.mem_op === MemOp.load
    val exe     = idu_out.fire

    // ========== 执行：源操作数 → ALU（接受拍组合） ==========
    // 注意：必须定义在 wb_data 之前（wb_data 引用 AluResult/PCAddFour）
    val src1 = WireDefault(0.U(32.W))
    switch(idu_out.bits.exu_ctl.ALU_Src1){
        is(ALUSrc1.rs1){ src1 := idu_out.bits.rs1_data }
        is(ALUSrc1.pc) { src1 := idu_out.bits.decode.pc }
    }
    val src2 = WireDefault(0.U(32.W))
    switch(idu_out.bits.exu_ctl.ALU_Src2){
        is(ALUSrc2.rs2)   { src2 := idu_out.bits.rs2_data }
        is(ALUSrc2.imm)   { src2 := idu_out.bits.decode.imm }
        is(ALUSrc2.shamt) { src2 := Cat(0.U(27.W), idu_out.bits.decode.imm(4,0)) }
        is(ALUSrc2.csr)   { src2 := idu_out.bits.csr_rdata }
    }
    val ALU_u = Module(new ysyx_26030092_ALU)
    ALU_u.in.ctl := idu_out.bits.exu_ctl.ALU_Control
    ALU_u.in.src1 := src1
    ALU_u.in.src2 := src2
    // dontTouch：防止被优化掉，便于波形调试观察 ALU 结果
    val AluResult = WireDefault(0.U(32.W))
    dontTouch(AluResult)
    AluResult := ALU_u.out
    val PCAddFour = idu_out.bits.decode.pc + 4.U

    // ========== 分支条件判定（接受拍组合） ==========
    val BranchCond = WireDefault(false.B)
    switch(idu_out.bits.exu_ctl.branch_op){
        is(BranchOp.beq)  { BranchCond := idu_out.bits.rs1_data === idu_out.bits.rs2_data }
        is(BranchOp.bne)  { BranchCond := idu_out.bits.rs1_data =/= idu_out.bits.rs2_data }
        is(BranchOp.blt)  { BranchCond := idu_out.bits.rs1_data.asSInt < idu_out.bits.rs2_data.asSInt }
        is(BranchOp.bge)  { BranchCond := idu_out.bits.rs1_data.asSInt >= idu_out.bits.rs2_data.asSInt }
        is(BranchOp.bltu) { BranchCond := idu_out.bits.rs1_data < idu_out.bits.rs2_data }
        is(BranchOp.bgeu) { BranchCond := idu_out.bits.rs1_data >= idu_out.bits.rs2_data }
    }

    // ========== 控制冒险：跳转目标统一 = AluResult（jal: pc+imm，jalr: rs1+imm，分支: pc+imm） ==========
    val is_jump = idu_out.bits.exu_ctl.jump_op === JumpOp.jump
    val is_mret = idu_out.bits.exu_ctl.sys_op === SysOp.mret
    val taken = BranchCond || is_jump
    val is_branch = idu_out.bits.exu_ctl.branch_op =/= BranchOp.none || is_jump
    val redirect = (is_branch && taken && AluResult =/= PCAddFour) || is_mret
    // 组合判定：执行拍的分支/跳转/ecall → 需要冲刷。锁存动作在状态机里与指令锁存同步
    val flush_comb = exe && !is_mem && (redirect || idu_out.bits.exu_ctl.sys_op === SysOp.ecall)
    val redirect_pc_comb = MuxCase(0.U(32.W), Seq(
        (is_branch && taken) -> AluResult,
        is_mret              -> idu_out.bits.csr_rdata,
        (idu_out.bits.exu_ctl.sys_op === SysOp.ecall) -> mtvec
    ))
    // flush / redirectPC 由寄存器驱动（EX 锁存后信号才有效），在状态机锁存块中同步锁存
    val flush_reg = RegInit(false.B)
    val redirect_pc_reg = RegInit(0.U(32.W))
    flush := flush_reg
    redirectPC := redirect_pc_reg

    // ========== 非访存写回组合值（接受拍生成，供 wb_reg / ex_pending 锁存） ==========
    val wb_data = Wire(new WriteBackPortBundle)
    wb_data := 0.U.asTypeOf(new WriteBackPortBundle)
    wb_data.RegWaddr := idu_out.bits.decode.rd
    wb_data.RegWrite := idu_out.bits.exu_ctl.reg_write
    switch(idu_out.bits.exu_ctl.wb_src){
        is(WBSrc.alu)      { wb_data.RegWdata := AluResult }
        is(WBSrc.pc_plus4) { wb_data.RegWdata := PCAddFour }
        is(WBSrc.csr)      { wb_data.RegWdata := idu_out.bits.csr_rdata }
    }
    // CSR 写端口（csrrs/csrrw）：csrrw 写 rs1 原值，csrrs 写 rs1|csr（即 AluResult）
    when(idu_out.bits.exu_ctl.csr_op =/= CSROp.none){
        wb_data.CSRAddr := idu_out.bits.decode.imm(11,0)
        wb_data.CSRWrite := true.B
        wb_data.CSRData := Mux(idu_out.bits.exu_ctl.csr_op === CSROp.csrrw,
                               idu_out.bits.rs1_data, AluResult)
    }
    // 系统指令：ecall 触发 trap
    when(idu_out.bits.exu_ctl.sys_op === SysOp.ecall){
        wb_data.Exception_Code := 11.U
        wb_data.PC := idu_out.bits.decode.pc
        wb_data.isTrap := true.B
    }
    wb_data.icache_flush := idu_out.bits.exu_ctl.fence_i

    switch(state){
        is(s_idle){
            idu_out.ready := ~(idu_out.valid && is_mem && ~lsu_in.ready) &&
                             ~wb_reg_valid && ~ex_pending_valid
            lsu_in.valid := exe && is_mem
            when(exe){
                when(is_mem){
                    // 访存：发 LSU，保存写回/提交信息
                    wb_rd      := idu_out.bits.decode.rd
                    wb_regwr   := is_load
                    wb_pc      := idu_out.bits.decode.pc
                    wb_memaddr := AluResult
                    state := s_wait_lsu_valid
                }.otherwise{
                    // 非访存：LSU 空闲（无更老访存）→ 直接进最终写回；忙 → 暂存等待
                    when(lsu_in.ready){
                        wb_reg := wb_data
                        wb_commit.pc := idu_out.bits.decode.pc
                        wb_commit.mem := false.B
                        wb_commit.mem_addr := 0.U
                        wb_reg_valid := true.B
                        // flush 与指令锁存同步（EX 锁存后信号才有效）
                        flush_reg := flush_comb
                        redirect_pc_reg := redirect_pc_comb
                    }.otherwise{
                        ex_pending := wb_data
                        ex_pending_commit.pc := idu_out.bits.decode.pc
                        ex_pending_commit.mem := false.B
                        ex_pending_commit.mem_addr := 0.U
                        ex_pending_valid := true.B
                        // flush 与指令锁存同步
                        flush_reg := flush_comb
                        redirect_pc_reg := redirect_pc_comb
                        state := s_wait_lsu_valid
                    }
                }
            }.otherwise{
                // 非执行拍：清除 flush（保证单拍脉冲）
                flush_reg := false.B
            }
        }
        is(s_wait_lsu_valid){
            lsu_out.ready := true.B
            when(lsu_out.valid){
                // LSU 返回（数据在其内部已锁存），直接锁存进最终写回
                wb_reg := 0.U.asTypeOf(new WriteBackPortBundle)
                wb_reg.RegWaddr := wb_rd
                wb_reg.RegWdata := lsu_out.bits.rdata
                wb_reg.RegWrite := wb_regwr
                wb_commit.pc := wb_pc
                wb_commit.mem := true.B
                wb_commit.mem_addr := wb_memaddr
                wb_reg_valid := true.B
                state := s_idle
            }
        }
    }

    // ========== 写回通路（wbu_in 由 wb_reg 驱动，fire 后 ex_pending 接力） ==========
    wbu_in.valid := wb_reg_valid
    wbu_in.bits := wb_reg
    commit_info := wb_commit
    when(wbu_in.fire){
        wb_reg_valid := false.B
        when(ex_pending_valid){
            wb_reg := ex_pending
            wb_commit := ex_pending_commit
            ex_pending_valid := false.B
            wb_reg_valid := true.B
        }
    }

    // ========== 无效指令检测（仿真黑盒，接受拍触发） ==========
    val invalid_instruction = Module(new InvalidInstructionBlackBox())
    invalid_instruction.io.pc := idu_out.bits.decode.pc
    invalid_instruction.io.inst := idu_out.bits.decode.inst
    invalid_instruction.io.invalid := exe && idu_out.bits.decode.invalid

    // ========== RAW 冒险检测信息 ==========
    // EX 层：非访存指令结果已锁存（由下面两槽覆盖），恒不命中
    ex_wb_info.wen := false.B
    ex_wb_info.rd  := 0.U
    // MEM 层：覆盖 访存等待期（wb_rd）+ 最终写回槽 + 暂存槽 三个窗口
    // （wb_reg_valid 与 state=s_wait_lsu_valid 互斥：等待期写回槽必空）
    mem_wb_info.wen := (state === s_wait_lsu_valid && wb_regwr) ||
                       (wb_reg_valid && wb_reg.RegWrite) ||
                       (ex_pending_valid && ex_pending.RegWrite)
    mem_wb_info.rd  := Mux(wb_reg_valid, wb_reg.RegWaddr,
                       Mux(ex_pending_valid, ex_pending.RegWaddr, wb_rd))
    // CSR 写后读：覆盖 最终写回槽 + 暂存槽（CSR 指令非访存，等待期无 CSR 写）
    ex_csr_info.wen  := (wb_reg_valid && wb_reg.CSRWrite) ||
                        (ex_pending_valid && ex_pending.CSRWrite)
    ex_csr_info.addr := Mux(wb_reg_valid, wb_reg.CSRAddr, ex_pending.CSRAddr)

    // 访存错误 / ebreak 指令：仿真终止（BAD TRAP）
    val ebreak = Module(new EbreakBlackBox())
    ebreak.io.ebreak_trap := false.B
    when(lsu_out.fire && lsu_out.bits.error === true.B){
        ebreak.io.ebreak_trap := true.B
    }
    when(exe && idu_out.bits.exu_ctl.sys_op === SysOp.ebreak){
        ebreak.io.ebreak_trap := true.B
    }

    // ========== 访存（接受拍发 LSU，LSU 忙时反压保证握手） ==========
    lsu_in.bits := 0.U.asTypeOf(new LSUBundle)
    lsu_in.bits.addr := AluResult
    lsu_in.bits.wdata := idu_out.bits.rs2_data
    lsu_in.bits.mem_op := idu_out.bits.exu_ctl.mem_op
    lsu_in.bits.mem_size := idu_out.bits.exu_ctl.mem_size
    lsu_in.bits.mem_signed := idu_out.bits.exu_ctl.mem_signed

    PerfCounter("EXU_finished") := wbu_in.fire
    // 控制冒险：冲刷次数（flush 由 EX 寄存器驱动，直接计数）
    PerfCounter("EXU_flush_count") := flush_reg
    // EXU 反压拍数（写回槽满 / 访存指令遇 LSU 忙），衡量流水线停顿
    PerfCounter("EXU_stall_cycles") := idu_out.valid && ~idu_out.ready
    // 非访存指令因 LSU 忙而暂存 ex_pending 的次数（访存延迟隐藏效果）
    PerfCounter("EXU_use_pending") := exe && ~is_mem && ~lsu_in.ready
    // EXU 完全空闲拍数（无执行、无待写回、无访存等待）
    PerfCounter("EXU_idle_cycles") := ~exe && state === s_idle && ~wb_reg_valid && ~ex_pending_valid
}
