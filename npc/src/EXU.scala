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

    // RAW 冒险检测信息：EX 级（执行/写回拍）与 MEM 级（访存等待）的写寄存器信息
    val ex_wb_info  = IO(Output(new StageWriteInfoBundle))
    val mem_wb_info = IO(Output(new StageWriteInfoBundle))
    val ex_csr_info = IO(Output(new CSRWriteInfoBundle))


    val idu_out_reg = Reg(new IDUBundle)

    val lsu_out_reg = Reg(new Bundle{
        val rdata = UInt(32.W)
        val error = Bool()
    })

    val s_idle :: s_wait_lsu_free :: s_wait_lsu_valid :: s_write_back :: Nil = Enum(4)
    val state = RegInit(s_idle)

    idu_out.ready := false.B
    lsu_out.ready := false.B
    lsu_in.valid := false.B
    wbu_in.valid := false.B

    // 接受拍判断是否访存（基于输入侧 idu_out.bits，此时 idu_out_reg 还是上一条指令）
    val is_mem = idu_out.bits.exu_ctl.mem_op =/= MemOp.none

    switch(state){
        is(s_idle){
            idu_out.ready := true.B
            when(idu_out.valid){
                idu_out_reg := idu_out.bits
                state := Mux(is_mem, s_wait_lsu_free, s_write_back)
            }
        }
        is(s_wait_lsu_free){
            lsu_in.valid := true.B
            when(lsu_in.ready){
                state := s_wait_lsu_valid
            }
        }
        is(s_wait_lsu_valid){
            lsu_out.ready := true.B
            when(lsu_out.valid){
                lsu_out_reg := lsu_out.bits
                state := s_write_back
            }
        }
        is(s_write_back){
            wbu_in.valid := true.B
            when(wbu_in.ready){
                state := s_idle
            }
        }
    }

    val invalid_instruction = Module(new InvalidInstructionBlackBox())
    invalid_instruction.io.pc := idu_out_reg.decode.pc
    invalid_instruction.io.inst := idu_out_reg.decode.inst
    invalid_instruction.io.invalid := (state === s_wait_lsu_free || state === s_write_back) &&
                                      idu_out_reg.decode.invalid

    val src1 = WireDefault(0.U(32.W))
    switch(idu_out_reg.exu_ctl.ALU_Src1){
        is(ALUSrc1.rs1){ src1 := idu_out_reg.rs1_data }
        is(ALUSrc1.pc) { src1 := idu_out_reg.decode.pc }
    }
    val src2 = WireDefault(0.U(32.W))
    switch(idu_out_reg.exu_ctl.ALU_Src2){
        is(ALUSrc2.rs2)   { src2 := idu_out_reg.rs2_data }
        is(ALUSrc2.imm)   { src2 := idu_out_reg.decode.imm }
        is(ALUSrc2.shamt) { src2 := Cat(0.U(27.W), idu_out_reg.decode.imm(4,0)) }
        is(ALUSrc2.csr)   { src2 := idu_out_reg.csr_rdata }
    }
    val ALU_u = Module(new ysyx_26030092_ALU)
    ALU_u.in.ctl := idu_out_reg.exu_ctl.ALU_Control
    ALU_u.in.src1 := src1
    ALU_u.in.src2 := src2
    // dontTouch：防止被优化掉，便于波形调试观察 ALU 结果
    val AluResult = WireDefault(0.U(32.W))
    dontTouch(AluResult)
    AluResult := ALU_u.out
    val PCAddFour = idu_out_reg.decode.pc + 4.U

    // 指令类型判定（译码信号直接给出，无推导）
    val is_jump = idu_out_reg.exu_ctl.jump_op === JumpOp.jump
    val is_mret = idu_out_reg.exu_ctl.sys_op === SysOp.mret
    val is_load = idu_out_reg.exu_ctl.mem_op === MemOp.load

    // RAW 检测信息：wen 已含阶段有效性（阶段空闲时为 0，rd 为垃圾值但不被使用）
    ex_wb_info.wen   := (state =/= s_idle) && idu_out_reg.exu_ctl.reg_write
    ex_wb_info.rd    := idu_out_reg.decode.rd
    mem_wb_info.wen  := (state === s_wait_lsu_free || state === s_wait_lsu_valid) && is_load
    mem_wb_info.rd   := idu_out_reg.decode.rd
    ex_csr_info.wen  := (state =/= s_idle) && (idu_out_reg.exu_ctl.csr_op =/= CSROp.none)
    ex_csr_info.addr := idu_out_reg.decode.imm(11,0)

    // 分支条件判定
    val BranchCond = WireDefault(false.B)
    switch(idu_out_reg.exu_ctl.branch_op){
        is(BranchOp.beq)  { BranchCond := idu_out_reg.rs1_data === idu_out_reg.rs2_data }
        is(BranchOp.bne)  { BranchCond := idu_out_reg.rs1_data =/= idu_out_reg.rs2_data }
        is(BranchOp.blt)  { BranchCond := idu_out_reg.rs1_data.asSInt < idu_out_reg.rs2_data.asSInt }
        is(BranchOp.bge)  { BranchCond := idu_out_reg.rs1_data.asSInt >= idu_out_reg.rs2_data.asSInt }
        is(BranchOp.bltu) { BranchCond := idu_out_reg.rs1_data < idu_out_reg.rs2_data }
        is(BranchOp.bgeu) { BranchCond := idu_out_reg.rs1_data >= idu_out_reg.rs2_data }
    }

    // 控制冒险：跳转目标统一 = AluResult（jal: pc+imm，jalr: rs1+imm，分支: pc+imm）
    val taken = BranchCond || is_jump
    val is_branch = idu_out_reg.exu_ctl.branch_op =/= BranchOp.none || is_jump
    val redirect = (is_branch && taken && AluResult =/= PCAddFour) || is_mret
    flush := wbu_in.fire && (redirect || wbu_in.bits.isTrap)
    redirectPC := MuxCase(0.U(32.W), Seq(
        (is_branch && taken) -> AluResult,
        is_mret              -> idu_out_reg.csr_rdata,
        wbu_in.bits.isTrap   -> mtvec
    ))

    // 访存错误 / ebreak 指令：仿真终止（BAD TRAP）
    val ebreak = Module(new EbreakBlackBox())
    ebreak.io.ebreak_trap := false.B
    when(lsu_out.fire && lsu_out.bits.error === true.B){
        ebreak.io.ebreak_trap := true.B
    }
    when(idu_out_reg.exu_ctl.sys_op === SysOp.ebreak){
        ebreak.io.ebreak_trap := true.B
    }

    // 写回：RegWrite/RegWaddr/RegWdata 由译码信号（reg_write/wb_src）统一决定
    wbu_in.bits := 0.U.asTypeOf(new WriteBackPortBundle)
    wbu_in.bits.RegWaddr := idu_out_reg.decode.rd
    wbu_in.bits.RegWrite := idu_out_reg.exu_ctl.reg_write
    switch(idu_out_reg.exu_ctl.wb_src){
        is(WBSrc.alu)      { wbu_in.bits.RegWdata := AluResult }
        is(WBSrc.mem)      { wbu_in.bits.RegWdata := lsu_out_reg.rdata }
        is(WBSrc.pc_plus4) { wbu_in.bits.RegWdata := PCAddFour }
        is(WBSrc.csr)      { wbu_in.bits.RegWdata := idu_out_reg.csr_rdata }
    }
    // CSR 写端口（csrrs/csrrw）：csrrw 写 rs1 原值，csrrs 写 rs1|csr（即 AluResult）
    when(idu_out_reg.exu_ctl.csr_op =/= CSROp.none){
        wbu_in.bits.CSRAddr := idu_out_reg.decode.imm(11,0)
        wbu_in.bits.CSRWrite := true.B
        wbu_in.bits.CSRData := Mux(idu_out_reg.exu_ctl.csr_op === CSROp.csrrw,
                                   idu_out_reg.rs1_data, AluResult)
    }
    // 系统指令：ecall 触发 trap
    when(idu_out_reg.exu_ctl.sys_op === SysOp.ecall){
        wbu_in.bits.Exception_Code := 11.U
        wbu_in.bits.PC := idu_out_reg.decode.pc
        wbu_in.bits.isTrap := true.B
    }
    wbu_in.bits.icache_flush := idu_out_reg.exu_ctl.fence_i

    // 访存
    lsu_in.bits := 0.U.asTypeOf(new LSUBundle)
    lsu_in.bits.addr := AluResult
    lsu_in.bits.wdata := idu_out_reg.rs2_data
    lsu_in.bits.mem_op := idu_out_reg.exu_ctl.mem_op
    lsu_in.bits.mem_size := idu_out_reg.exu_ctl.mem_size
    lsu_in.bits.mem_signed := idu_out_reg.exu_ctl.mem_signed

    commit_info.pc := idu_out_reg.decode.pc
    commit_info.mem := idu_out_reg.exu_ctl.mem_op =/= MemOp.none
    commit_info.mem_addr := lsu_in.bits.addr

    PerfCounter("EXU_finished") := (state === s_write_back && wbu_in.ready)
}
