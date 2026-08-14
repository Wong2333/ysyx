package npc

import chisel3._
import chisel3.util._
import npc.EXUs._
import npc.PMU._


object ALUOp extends ChiselEnum{
    val RType,
    IType,
    SType,
    JType,
    UType,
    BType = Value
}



object Jump extends ChiselEnum{
    val none,
    jal = Value
}

object MemControl extends ChiselEnum{
    val none,
    lbu,
    lhu,
    lb,
    lh,
    lw,
    
    sw,
    sb,
    sh = Value
}

object BranchControl extends ChiselEnum{
    val none,
    beq,
    bne,
    blt,
    bge,
    bltu,
    bgeu = Value
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

    val idu_out = IO(Flipped(Decoupled(new IDUBundle)))
    val wbu_in = IO(Decoupled(new WriteBackPortBundle))

    val lsu_in = IO(Decoupled(new LSUBundle))
    val lsu_out = IO(Flipped(Decoupled(new Bundle {
        val rdata = Input(UInt(32.W))
        val error = Input(Bool())
    })))
    
    // 提交指令信息（PC / 是否访存 / 访存地址），沿流水线交给 WBU → RFS
    val commit_info = IO(Output(new CommitInfoBundle))

    val idu_out_reg = Reg(new IDUBundle)
    val lsu_out_reg = Reg(new Bundle{
        val rdata = UInt(32.W)
        val error = Bool()
    })

    // io.out := 0.U
    val AluResult = WireDefault(0.U(32.W))
    dontTouch(AluResult)
    val PCAddFour = idu_out.bits.decode.pc + 4.U
    
    idu_out.ready := false.B
    lsu_out.ready := false.B
    lsu_in.valid := false.B
    wbu_in.valid := false.B

    val invalid_instruction = Module(new InvalidInstructionBlackBox())
    invalid_instruction.io.pc := idu_out_reg.decode.pc
    invalid_instruction.io.inst := idu_out_reg.decode.inst
    invalid_instruction.io.invalid := false.B

    // val idu_out_valid = RegInit(false.B)

    val s_idle :: s_wait_lsu_free :: s_wait_lsu_valid :: s_write_back :: Nil = Enum(4)
    val status = RegInit(s_idle)
    val is_mem = idu_out.bits.exu_ctl.Mem_Control =/= MemControl.none

    // 提交指令信息：PC / 是否访存 / 访存地址（沿流水线交给 WBU → RFS）
    commit_info.pc := idu_out_reg.decode.pc
    commit_info.mem := idu_out_reg.exu_ctl.Mem_Control =/= MemControl.none
    commit_info.mem_addr := lsu_in.bits.addr

    switch(status){
        is(s_idle){
            idu_out.ready := true.B
            when(idu_out.valid){
                idu_out_reg := idu_out.bits
                when(is_mem){
                    status := s_wait_lsu_free
                }.otherwise{
                    status := s_write_back
                }
            }
        }
        is(s_wait_lsu_free){
            lsu_in.valid := true.B
            invalid_instruction.io.invalid := idu_out_reg.decode.invalid
            when(lsu_in.ready){
                status := s_wait_lsu_valid
            }
        }
        is(s_wait_lsu_valid){
            lsu_out.ready := true.B
            when(lsu_out.valid){
                lsu_out_reg := lsu_out.bits
                status := s_write_back
            }
        }
        is(s_write_back){
            wbu_in.valid := true.B
            invalid_instruction.io.invalid := idu_out_reg.decode.invalid
            when(wbu_in.ready){
                status := s_idle
            }
        }
    }


    val ebreak = Module(new EbreakBlackBox())
    ebreak.io.ebreak_trap := false.B
    when(lsu_out.fire && lsu_out.bits.error === true.B){
        ebreak.io.ebreak_trap := true.B
    }

    val src1 = WireDefault(0.U(32.W))
    switch(idu_out_reg.exu_ctl.ALU_Src1){
        is(ALUSrc1.rs1){
            src1 := idu_out_reg.rs1_data
        }
        is(ALUSrc1.pc){
            src1 := idu_out_reg.decode.pc
        }
    }
    val src2 = WireDefault(0.U(32.W))
    switch(idu_out_reg.exu_ctl.ALU_Src2){
        is(ALUSrc2.rs2){
            src2 := idu_out_reg.rs2_data
        }
        is(ALUSrc2.imm){
            src2 := idu_out_reg.decode.imm
        }
        is(ALUSrc2.shamt){
            src2 := Cat(0.U(27.W),idu_out_reg.decode.imm(4,0))
        }
        is(ALUSrc2.csr){
            src2 := idu_out_reg.csr_rdata
        }
    }
    val ALU_u = Module(new ysyx_26030092_ALU)
    ALU_u.in.ctl := idu_out_reg.exu_ctl.ALU_Control
    ALU_u.in.src1 := src1
    ALU_u.in.src2 := src2
    AluResult := ALU_u.out


    wbu_in.bits := 0.U.asTypeOf(new WriteBackPortBundle)
    when(idu_out_reg.exu_ctl.ALUWrite2Reg){
        wbu_in.bits.RegWaddr := idu_out_reg.decode.rd
        wbu_in.bits.RegWrite := true.B
        wbu_in.bits.RegWdata := AluResult
    }

    val CSRController_u = Module(new ysyx_26030092_CSRController)
    CSRController_u.in.AluResult := AluResult
    CSRController_u.in.ctl := idu_out_reg.exu_ctl.CSR_Contorl
    CSRController_u.in.decode := idu_out_reg.decode
    CSRController_u.in.rs1_data := idu_out_reg.rs1_data
    CSRController_u.in.csr_rdata := idu_out_reg.csr_rdata
    when(idu_out_reg.exu_ctl.CSR_Contorl =/= CSRControl.none){
        wbu_in.bits := CSRController_u.out
    }
    
    switch(idu_out_reg.exu_ctl.ALU_Jump){
        is(Jump.jal){
            wbu_in.bits.PCWrite := true.B
            wbu_in.bits.PCNext := AluResult
            wbu_in.bits.RegWaddr := idu_out_reg.decode.rd
            wbu_in.bits.RegWrite := true.B
            wbu_in.bits.RegWdata := PCAddFour
        }
    }

    val BranchCond = WireDefault(false.B)
    switch(idu_out_reg.exu_ctl.Branch_Control){
        is(BranchControl.beq){
            BranchCond := idu_out_reg.rs1_data === idu_out_reg.rs2_data
        }
        is(BranchControl.bne){
            BranchCond := idu_out_reg.rs1_data =/= idu_out_reg.rs2_data
        }
        is(BranchControl.blt){
            BranchCond := idu_out_reg.rs1_data.asSInt < idu_out_reg.rs2_data.asSInt
        }
        is(BranchControl.bge){
            BranchCond := idu_out_reg.rs1_data.asSInt >= idu_out_reg.rs2_data.asSInt
        }
        is(BranchControl.bltu){
            BranchCond := idu_out_reg.rs1_data < idu_out_reg.rs2_data
        }
        is(BranchControl.bgeu){
            BranchCond := idu_out_reg.rs1_data >= idu_out_reg.rs2_data
        }
    }
    when(BranchCond){
        wbu_in.bits.PCWrite := true.B
        wbu_in.bits.PCNext := AluResult
        wbu_in.bits.RegWrite := false.B
    }

    lsu_in.bits := 0.U.asTypeOf(new LSUBundle)

    when(idu_out_reg.exu_ctl.Mem_Control.isOneOf(MemControl.lb,MemControl.lbu,MemControl.lh,
        MemControl.lhu,MemControl.lw)){
        lsu_in.bits.addr := AluResult
        wbu_in.bits.RegWaddr := idu_out_reg.decode.rd
        wbu_in.bits.RegWrite := true.B
        wbu_in.bits.RegWdata := lsu_out_reg.rdata
    }.elsewhen(idu_out_reg.exu_ctl.Mem_Control.isOneOf(MemControl.sb,MemControl.sh,MemControl.sw)){
        lsu_in.bits.addr := AluResult
        lsu_in.bits.wdata := idu_out_reg.rs2_data
    }
    lsu_in.bits.mem_ctl := idu_out_reg.exu_ctl.Mem_Control

    wbu_in.bits.icache_flush := idu_out_reg.exu_ctl.fence_i

    PerfCounter("EXU_finished") := (status === s_write_back && wbu_in.ready)
}