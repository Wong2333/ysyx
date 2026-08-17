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

    // RAW 冒险检测信息（非访存指令的写回窗口由 ex_pending/mem_pending 覆盖）
    val ex_wb_info  = IO(Output(new StageWriteInfoBundle))
    val mem_wb_info = IO(Output(new StageWriteInfoBundle))
    val ex_csr_info = IO(Output(new CSRWriteInfoBundle))
    val mem_csr_info = IO(Output(new CSRWriteInfoBundle))

    // 非访存暂存槽：LSU 忙时锁存ex计算结果
    val ex_pending = Reg(new WriteBackPortBundle)
    val ex_mem_pending = Reg(new Bundle {
        val mem_op = MemOp()
        val mem_addr = UInt(32.W)
        val mem_wdata = UInt(32.W)
        val mem_size = MemSize()
        val mem_signed = Bool()
    })
    val ex_pending_valid = RegInit(false.B)
    val ex_pending_commit = Reg(new CommitInfoBundle)

    idu_out.ready := false.B
    lsu_out.ready := false.B
    lsu_in.valid := false.B
    wbu_in.valid := false.B

    // 接受拍判定（基于输入侧 idu_out.bits，接受拍即执行拍）
    val is_mem  = idu_out.bits.exu_ctl.mem_op =/= MemOp.none
    val is_load = idu_out.bits.exu_ctl.mem_op === MemOp.load
    val fire_s0     = idu_out.fire

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

    val BranchCond = WireDefault(false.B)
    switch(idu_out.bits.exu_ctl.branch_op){
        is(BranchOp.beq)  { BranchCond := idu_out.bits.rs1_data === idu_out.bits.rs2_data }
        is(BranchOp.bne)  { BranchCond := idu_out.bits.rs1_data =/= idu_out.bits.rs2_data }
        is(BranchOp.blt)  { BranchCond := idu_out.bits.rs1_data.asSInt < idu_out.bits.rs2_data.asSInt }
        is(BranchOp.bge)  { BranchCond := idu_out.bits.rs1_data.asSInt >= idu_out.bits.rs2_data.asSInt }
        is(BranchOp.bltu) { BranchCond := idu_out.bits.rs1_data < idu_out.bits.rs2_data }
        is(BranchOp.bgeu) { BranchCond := idu_out.bits.rs1_data >= idu_out.bits.rs2_data }
    }

    // 控制冒险
    val is_jump = idu_out.bits.exu_ctl.jump_op === JumpOp.jump
    val is_mret = idu_out.bits.exu_ctl.sys_op === SysOp.mret
    val taken = BranchCond || is_jump
    val is_branch = idu_out.bits.exu_ctl.branch_op =/= BranchOp.none || is_jump
    val redirect = (is_branch && taken && AluResult =/= PCAddFour) || is_mret
    val flush_comb = fire_s0 && !is_mem && (redirect || idu_out.bits.exu_ctl.sys_op === SysOp.ecall)
    val redirect_pc_comb = MuxCase(0.U(32.W), Seq(
        (is_branch && taken) -> AluResult,
        is_mret              -> idu_out.bits.csr_rdata,
        (idu_out.bits.exu_ctl.sys_op === SysOp.ecall) -> mtvec
    ))

    val flush_reg = RegInit(false.B)
    val redirect_pc_reg = RegInit(0.U(32.W))
    flush := flush_reg
    redirectPC := redirect_pc_reg

    val wb_data = Wire(new WriteBackPortBundle)
    wb_data := 0.U.asTypeOf(new WriteBackPortBundle)
    wb_data.RegWaddr := idu_out.bits.decode.rd
    wb_data.RegWrite := idu_out.bits.exu_ctl.reg_write
    wb_data.PC := idu_out.bits.decode.pc
    switch(idu_out.bits.exu_ctl.wb_src){
        is(WBSrc.alu)      { wb_data.RegWdata := AluResult }
        is(WBSrc.pc_plus4) { wb_data.RegWdata := PCAddFour }
        is(WBSrc.csr)      { wb_data.RegWdata := idu_out.bits.csr_rdata }
    }
    // CSR 写端口
    when(idu_out.bits.exu_ctl.csr_op =/= CSROp.none){
        wb_data.CSRAddr := idu_out.bits.decode.imm(11,0)
        wb_data.CSRWrite := true.B
        wb_data.CSRData := Mux(idu_out.bits.exu_ctl.csr_op === CSROp.csrrw,
                               idu_out.bits.rs1_data, AluResult)
    }
    // ecall 触发 trap
    when(idu_out.bits.exu_ctl.sys_op === SysOp.ecall){
        wb_data.Exception_Code := 11.U
        wb_data.PC := idu_out.bits.decode.pc
        wb_data.isTrap := true.B
    }
    wb_data.icache_flush := idu_out.bits.exu_ctl.fence_i

    // ex 流水线
    val ex_pending_ready = WireDefault(false.B)
    val ex_pending_fire = ex_pending_ready & ex_pending_valid

    when(!ex_pending_valid || ex_pending_fire){
        when(ex_pending_fire){ ex_pending_valid := false.B}
        when(idu_out.valid){
            idu_out.ready := true.B
            ex_pending := wb_data
            ex_mem_pending.mem_addr := AluResult
            ex_mem_pending.mem_op := idu_out.bits.exu_ctl.mem_op
            ex_mem_pending.mem_wdata := idu_out.bits.rs2_data
            ex_mem_pending.mem_size := idu_out.bits.exu_ctl.mem_size
            ex_mem_pending.mem_signed := idu_out.bits.exu_ctl.mem_signed
            ex_pending_valid := true.B

            ex_pending_commit.mem := is_mem // trace or difftest
            ex_pending_commit.mem_addr := AluResult
            ex_pending_commit.pc := idu_out.bits.decode.pc
        }
    }
    when(idu_out.fire){//锁存ex_pending时进行控制冒险检测
        flush_reg := flush_comb
        redirect_pc_reg := redirect_pc_comb
    }.otherwise{
        flush_reg := false.B
    }

    // mem 流水线 以及 lsu状态机
    val mem_pending = Reg(new WriteBackPortBundle)
    val mem_pending_ready = WireDefault(false.B)
    val mem_pending_valid = RegInit(false.B)
    val mem_pending_commit = Reg(new CommitInfoBundle)
    val mem_pending_fire = mem_pending_ready & mem_pending_valid
    val ex_pending_is_mem = ex_mem_pending.mem_op =/= MemOp.none 
    val lsu_busy = RegInit(false.B)

    when(mem_pending_fire || !mem_pending_valid){
        when(mem_pending_fire) { mem_pending_valid := false.B }
        when(lsu_busy){ //lsu忙，等待数据
            lsu_out.ready := true.B
            when(lsu_out.fire){
                mem_pending.RegWdata := lsu_out.bits.rdata
                mem_pending_valid := true.B
                lsu_busy := false.B
            }
        }.elsewhen(ex_pending_valid && ex_pending_is_mem){ //ex是访存指令
            lsu_in.valid := true.B
            when(lsu_in.fire){
                mem_pending := ex_pending
                mem_pending_commit := ex_pending_commit
                ex_pending_ready := true.B
                lsu_busy := true.B
            }
        }.elsewhen(ex_pending_valid && !ex_pending_is_mem){ //ex不访存
            mem_pending := ex_pending
            mem_pending_commit := ex_pending_commit
            ex_pending_ready := true.B
            mem_pending_valid := true.B
        }
    }

    // wb 流水线
    wbu_in.valid := mem_pending_valid
    wbu_in.bits := mem_pending
    mem_pending_ready := wbu_in.ready
    commit_info := mem_pending_commit

    val invalid_instruction = Module(new InvalidInstructionBlackBox())
    invalid_instruction.io.pc := idu_out.bits.decode.pc
    invalid_instruction.io.inst := idu_out.bits.decode.inst
    invalid_instruction.io.invalid := fire_s0 && idu_out.bits.decode.invalid

    // RAW 冒险检测
    ex_wb_info.wen := ex_pending_valid && ex_pending.RegWrite
    ex_wb_info.rd  := ex_pending.RegWaddr
    
    mem_wb_info.wen := (mem_pending_valid || lsu_busy) && mem_pending.RegWrite
    mem_wb_info.rd  := mem_pending.RegWaddr
    // CSR 写后读：ex_pending 槽与 mem_pending 槽各一个端口（两槽可同时有 CSR 写，地址需分别比较）
    ex_csr_info.wen  := ex_pending_valid && ex_pending.CSRWrite
    ex_csr_info.addr := ex_pending.CSRAddr
    mem_csr_info.wen := (mem_pending_valid || lsu_busy) && mem_pending.CSRWrite
    mem_csr_info.addr := mem_pending.CSRAddr

    // 访存错误 / ebreak 指令：仿真终止（BAD TRAP）
    val ebreak = Module(new EbreakBlackBox())
    ebreak.io.ebreak_trap := false.B
    when(lsu_out.fire && lsu_out.bits.error === true.B){
        ebreak.io.ebreak_trap := true.B
    }
    when(fire_s0 && idu_out.bits.exu_ctl.sys_op === SysOp.ebreak){
        ebreak.io.ebreak_trap := true.B
    }

    // lsu访存请求
    lsu_in.bits := 0.U.asTypeOf(new LSUBundle)
    lsu_in.bits.addr := ex_mem_pending.mem_addr
    lsu_in.bits.wdata := ex_mem_pending.mem_wdata
    lsu_in.bits.mem_op := ex_mem_pending.mem_op
    lsu_in.bits.mem_size := ex_mem_pending.mem_size
    lsu_in.bits.mem_signed := ex_mem_pending.mem_signed

    PerfCounter("EXU_finished") := wbu_in.fire
    // 控制冒险：冲刷次数（flush 由 EX 寄存器驱动，直接计数）
    PerfCounter("EXU_flush_count") := flush_reg
    // EXU 反压拍数（写回槽满 / 访存指令遇 LSU 忙），衡量流水线停顿
    PerfCounter("EXU_stall_cycles") := idu_out.valid && ~idu_out.ready
    // 非访存指令因 LSU 忙而暂存 ex_pending 的次数（访存延迟隐藏效果）
    PerfCounter("EXU_use_pending") := fire_s0 && ~is_mem && ~lsu_in.ready
    // EXU 完全空闲拍数（无执行、无待写回、无访存等待）
    PerfCounter("EXU_idle_cycles") := ~fire_s0 && ~ex_pending_valid && ~mem_pending_valid && ~lsu_busy
}
