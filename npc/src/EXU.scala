package npc

import chisel3._
import chisel3.util._
import npc.EXUs._
import npc.PMU._

object ALUOp extends ChiselEnum{
    val RType, IType, SType, JType, UType, BType = Value
}

object Jump extends ChiselEnum{
    val none, jal = Value
}

object MemControl extends ChiselEnum{
    val none, lbu, lhu, lb, lh, lw, sw, sb, sh = Value
}

object BranchControl extends ChiselEnum{
    val none, beq, bne, blt, bge, bltu, bgeu = Value
}

object CSRControl extends ChiselEnum{
    val none, ebreak, ecall, mret, csrrw, csrrs = Value
}

class EXUCTLBundle extends Bundle{
    val ALU_Op = Output(ALUOp())
    val ALU_Src1 = Output(ALUSrc1())
    val ALU_Src2 = Output(ALUSrc2())
    val ALU_Control = Output(ALUContorl())
    val ALUWrite2Reg = Output(Bool())
    val CSR_Contorl = Output(CSRControl())
    val ALU_Jump = Output(Jump())
    val Mem_Control = Output(MemControl())
    val Branch_Control = Output(BranchControl())
    val fence_i = Output(Bool())
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
    val is_mem = idu_out.bits.exu_ctl.Mem_Control =/= MemControl.none

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

    // 指令类型判定（互斥）
    val is_jal  = idu_out_reg.exu_ctl.ALU_Jump === Jump.jal
    val is_csr  = idu_out_reg.exu_ctl.CSR_Contorl =/= CSRControl.none
    val is_mret = idu_out_reg.exu_ctl.CSR_Contorl === CSRControl.mret
    val is_load = idu_out_reg.exu_ctl.Mem_Control.isOneOf(MemControl.lb, MemControl.lbu,
                   MemControl.lh, MemControl.lhu, MemControl.lw)

    ex_wb_info.valid := (state =/= s_idle) || state === s_write_back
    ex_wb_info.wen   := idu_out_reg.exu_ctl.ALUWrite2Reg || is_jal ||
                        (is_csr && (idu_out_reg.exu_ctl.CSR_Contorl === CSRControl.csrrs ||
                                    idu_out_reg.exu_ctl.CSR_Contorl === CSRControl.csrrw))
    ex_wb_info.rd    := idu_out_reg.decode.rd
    mem_wb_info.valid := state === s_wait_lsu_free || state === s_wait_lsu_valid
    mem_wb_info.wen   := is_load
    mem_wb_info.rd    := idu_out_reg.decode.rd
    ex_csr_info.valid := (state =/= s_idle) || state === s_write_back
    ex_csr_info.wen   := is_csr && (idu_out_reg.exu_ctl.CSR_Contorl === CSRControl.csrrs ||
                                    idu_out_reg.exu_ctl.CSR_Contorl === CSRControl.csrrw)
    ex_csr_info.addr  := idu_out_reg.decode.imm(11,0)

    // 分支条件判定
    val BranchCond = WireDefault(false.B)
    switch(idu_out_reg.exu_ctl.Branch_Control){
        is(BranchControl.beq)  { BranchCond := idu_out_reg.rs1_data === idu_out_reg.rs2_data }
        is(BranchControl.bne)  { BranchCond := idu_out_reg.rs1_data =/= idu_out_reg.rs2_data }
        is(BranchControl.blt)  { BranchCond := idu_out_reg.rs1_data.asSInt < idu_out_reg.rs2_data.asSInt }
        is(BranchControl.bge)  { BranchCond := idu_out_reg.rs1_data.asSInt >= idu_out_reg.rs2_data.asSInt }
        is(BranchControl.bltu) { BranchCond := idu_out_reg.rs1_data < idu_out_reg.rs2_data }
        is(BranchControl.bgeu) { BranchCond := idu_out_reg.rs1_data >= idu_out_reg.rs2_data }
    }

    // 控制冒险
    val taken = BranchCond || is_jal
    val is_branch = idu_out_reg.exu_ctl.Branch_Control =/= BranchControl.none || is_jal
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
    when(is_csr && idu_out_reg.exu_ctl.CSR_Contorl === CSRControl.ebreak){
        ebreak.io.ebreak_trap := true.B
    }

    // 写回
    wbu_in.bits := 0.U.asTypeOf(new WriteBackPortBundle)
    when(is_csr){
        // CSR 指令（ecall / ebreak / mret / csrrs / csrrw）
        switch(idu_out_reg.exu_ctl.CSR_Contorl){
            is(CSRControl.ecall){
                wbu_in.bits.Exception_Code := 11.U
                wbu_in.bits.PC := idu_out_reg.decode.pc
                wbu_in.bits.isTrap := true.B
            }
            is(CSRControl.csrrs){
                wbu_in.bits.RegWaddr := idu_out_reg.decode.rd
                wbu_in.bits.RegWdata := idu_out_reg.csr_rdata
                wbu_in.bits.RegWrite := true.B
                wbu_in.bits.CSRAddr := idu_out_reg.decode.imm(11,0)
                wbu_in.bits.CSRData := AluResult
                wbu_in.bits.CSRWrite := true.B
            }
            is(CSRControl.csrrw){
                wbu_in.bits.RegWaddr := idu_out_reg.decode.rd
                wbu_in.bits.RegWdata := idu_out_reg.csr_rdata
                wbu_in.bits.RegWrite := true.B
                wbu_in.bits.CSRAddr := idu_out_reg.decode.imm(11,0)
                wbu_in.bits.CSRData := idu_out_reg.rs1_data
                wbu_in.bits.CSRWrite := true.B
            }
            is(CSRControl.mret){
                // 重定向目标 mepc 已由上面的 redirectPC 处理
            }
            is(CSRControl.ebreak){
                // 触发 ebreak_trap（见上），无写回
            }
        }
    }.otherwise{
        wbu_in.bits.RegWaddr := idu_out_reg.decode.rd
        wbu_in.bits.RegWrite := MuxCase(false.B, Seq(
            is_jal                               -> true.B,
            is_load                              -> true.B,
            idu_out_reg.exu_ctl.ALUWrite2Reg     -> true.B
        ))
        wbu_in.bits.RegWdata := MuxCase(0.U(32.W), Seq(
            is_jal                               -> PCAddFour,
            is_load                              -> lsu_out_reg.rdata,
            idu_out_reg.exu_ctl.ALUWrite2Reg     -> AluResult
        ))
    }
    wbu_in.bits.icache_flush := idu_out_reg.exu_ctl.fence_i

    // 访存
    lsu_in.bits := 0.U.asTypeOf(new LSUBundle)
    lsu_in.bits.addr := AluResult
    lsu_in.bits.wdata := idu_out_reg.rs2_data
    lsu_in.bits.mem_ctl := idu_out_reg.exu_ctl.Mem_Control

    commit_info.pc := idu_out_reg.decode.pc
    commit_info.mem := idu_out_reg.exu_ctl.Mem_Control =/= MemControl.none
    commit_info.mem_addr := lsu_in.bits.addr

    PerfCounter("EXU_finished") := (state === s_write_back && wbu_in.ready)
}
