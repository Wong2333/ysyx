package npc

import chisel3._
import chisel3.util._

object ALUOp extends ChiselEnum{
    val RType,
    IType,
    SType,
    JType,
    UType,
    BType = Value
}

object ALUSrc1 extends ChiselEnum{
    val rs1,
    pc = Value
}

object ALUSrc2 extends ChiselEnum{
    val rs2,
    imm,
    shamt,
    csr = Value
}

object ALUContorl extends ChiselEnum{
    val none,
    add,
    sub,
    and,
    or,
    xor,
    lui,
    sll,srl,sra, //左移右移
    slt,sltu, //小于
    equ = Value
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

object CSRControl extends ChiselEnum{
    val none,
    ebreak,
    ecall,
    mret,
    csrrw,
    csrrs = Value
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
}



class EXU extends Module{

    val RFS_ReadPort = IO(new Bundle {
        val RegReadPort = new ReadRegPortBundle
        val CSRReadPort = new CSRReadPortBundle
    })

    val idu_out = IO(Flipped(Decoupled(new DecodeBundle)))
    val wbu_in = IO(Decoupled(new WriteBackPortBundle))

    val lsu_in = IO(Decoupled(new LSUBundle))
    val lsu_out = IO(Flipped(Decoupled(new Bundle {
        val rdata = Input(UInt(32.W))
        val error = Input(Bool())
    })))

    val idu_out_reg = Reg(new DecodeBundle)
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

    RFS_ReadPort.RegReadPort.raddr1 := 0.U
    RFS_ReadPort.RegReadPort.raddr2 := 0.U
    val src1 = WireDefault(0.U(32.W))
    switch(idu_out_reg.exu_ctl.ALU_Src1){
        is(ALUSrc1.rs1){
            RFS_ReadPort.RegReadPort.raddr1 := idu_out_reg.decode.rs1
            src1 := RFS_ReadPort.RegReadPort.rdata1
        }
        is(ALUSrc1.pc){
            src1 := idu_out_reg.decode.pc
        }
    }
    val src2 = WireDefault(0.U(32.W))
    switch(idu_out_reg.exu_ctl.ALU_Src2){
        is(ALUSrc2.rs2){
            RFS_ReadPort.RegReadPort.raddr2 := idu_out_reg.decode.rs2
            src2 := RFS_ReadPort.RegReadPort.rdata2
        }
        is(ALUSrc2.imm){
            src2 := idu_out_reg.decode.imm
        }
        is(ALUSrc2.shamt){
            src2 := Cat(0.U(27.W),idu_out_reg.decode.imm(4,0))
        }
        is(ALUSrc2.csr){
            RFS_ReadPort.CSRReadPort.CSRRAddr := idu_out_reg.decode.imm(11,0)
            src2 := RFS_ReadPort.CSRReadPort.CSRRData
        }
    }

    switch(idu_out_reg.exu_ctl.ALU_Control){
        is(ALUContorl.add){
            AluResult := src1 + src2
        }
        is(ALUContorl.sub){
            AluResult := src1 - src2
        }
        is(ALUContorl.sltu){
            AluResult := src1 < src2
        }
        is(ALUContorl.slt){
            AluResult := src1.asSInt < src2.asSInt
        }
        is(ALUContorl.and){
            AluResult := src1 & src2
        }
        is(ALUContorl.xor){
            AluResult := src1 ^ src2
        }
        is(ALUContorl.or){
            AluResult := src1 | src2
        }
        is(ALUContorl.equ){
            AluResult := src1 === src2
        }
        is(ALUContorl.lui){
            AluResult := idu_out_reg.decode.imm
        }
        is(ALUContorl.sll){
            AluResult := src1 << src2(4,0)
        }
        is(ALUContorl.srl){
            AluResult := src1 >> src2(4,0)
        }
        is(ALUContorl.sra){
            AluResult := (src1.asSInt >> src2(4,0)).asUInt
        }
    }
    wbu_in.bits := 0.U.asTypeOf(new WriteBackPortBundle)
    when(idu_out_reg.exu_ctl.ALUWrite2Reg){
        wbu_in.bits.RegWaddr := idu_out_reg.decode.rd
        wbu_in.bits.RegWrite := true.B
        wbu_in.bits.RegWdata := AluResult
    }
    RFS_ReadPort.CSRReadPort.CSRRAddr := 0.U
    switch(idu_out_reg.exu_ctl.CSR_Contorl){
        is(CSRControl.ebreak){
            ebreak.io.ebreak_trap := true.B
        }
        is(CSRControl.ecall){
            wbu_in.bits.Exception_Code := 11.U
            wbu_in.bits.PC := idu_out_reg.decode.pc
            wbu_in.bits.isTrap := true.B
        }
        is(CSRControl.mret){
            RFS_ReadPort.CSRReadPort.CSRRAddr := "h341".U
            wbu_in.bits.PCNext := RFS_ReadPort.CSRReadPort.CSRRData
            wbu_in.bits.PCWrite := true.B
        }
        is(CSRControl.csrrs){
            RFS_ReadPort.CSRReadPort.CSRRAddr := idu_out_reg.decode.imm(11,0)
            wbu_in.bits.RegWaddr := idu_out_reg.decode.rd
            wbu_in.bits.RegWdata := RFS_ReadPort.CSRReadPort.CSRRData
            wbu_in.bits.RegWrite := true.B
            wbu_in.bits.CSRAddr := idu_out_reg.decode.imm(11,0)
            wbu_in.bits.CSRData := AluResult
            wbu_in.bits.CSRWrite := true.B
        }
        is(CSRControl.csrrw){
            RFS_ReadPort.RegReadPort.raddr1 := idu_out_reg.decode.rs1
            RFS_ReadPort.CSRReadPort.CSRRAddr := idu_out_reg.decode.imm(11,0)
            wbu_in.bits.RegWaddr := idu_out_reg.decode.rd
            wbu_in.bits.RegWdata := RFS_ReadPort.CSRReadPort.CSRRData
            wbu_in.bits.RegWrite := true.B
            wbu_in.bits.CSRAddr := idu_out_reg.decode.imm(11,0)
            wbu_in.bits.CSRData := RFS_ReadPort.RegReadPort.rdata1
            wbu_in.bits.CSRWrite := true.B
        }
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
            RFS_ReadPort.RegReadPort.raddr1 := idu_out_reg.decode.rs1
            RFS_ReadPort.RegReadPort.raddr2 := idu_out_reg.decode.rs2
            BranchCond := RFS_ReadPort.RegReadPort.rdata1 === RFS_ReadPort.RegReadPort.rdata2
        }
        is(BranchControl.bne){
            RFS_ReadPort.RegReadPort.raddr1 := idu_out_reg.decode.rs1
            RFS_ReadPort.RegReadPort.raddr2 := idu_out_reg.decode.rs2
            BranchCond := RFS_ReadPort.RegReadPort.rdata1 =/= RFS_ReadPort.RegReadPort.rdata2
        }
        is(BranchControl.blt){
            RFS_ReadPort.RegReadPort.raddr1 := idu_out_reg.decode.rs1
            RFS_ReadPort.RegReadPort.raddr2 := idu_out_reg.decode.rs2
            BranchCond := RFS_ReadPort.RegReadPort.rdata1.asSInt < RFS_ReadPort.RegReadPort.rdata2.asSInt
        }
        is(BranchControl.bge){
            RFS_ReadPort.RegReadPort.raddr1 := idu_out_reg.decode.rs1
            RFS_ReadPort.RegReadPort.raddr2 := idu_out_reg.decode.rs2
            BranchCond := RFS_ReadPort.RegReadPort.rdata1.asSInt >= RFS_ReadPort.RegReadPort.rdata2.asSInt
        }
        is(BranchControl.bltu){
            RFS_ReadPort.RegReadPort.raddr1 := idu_out_reg.decode.rs1
            RFS_ReadPort.RegReadPort.raddr2 := idu_out_reg.decode.rs2
            BranchCond := RFS_ReadPort.RegReadPort.rdata1 < RFS_ReadPort.RegReadPort.rdata2
        }
        is(BranchControl.bgeu){
            RFS_ReadPort.RegReadPort.raddr1 := idu_out_reg.decode.rs1
            RFS_ReadPort.RegReadPort.raddr2 := idu_out_reg.decode.rs2
            BranchCond := RFS_ReadPort.RegReadPort.rdata1 >= RFS_ReadPort.RegReadPort.rdata2
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
        RFS_ReadPort.RegReadPort.raddr2 := idu_out_reg.decode.rs2
        lsu_in.bits.wdata := RFS_ReadPort.RegReadPort.rdata2
    }
    switch(idu_out_reg.exu_ctl.Mem_Control){
        is(MemControl.lw){
            lsu_in.bits.mem_ctl := MEM_CTL.Read4B
        }
        is(MemControl.lbu){
            lsu_in.bits.mem_ctl := MEM_CTL.Read1BU
        }
        is(MemControl.lb){
            lsu_in.bits.mem_ctl := MEM_CTL.Read1B
        }
        is(MemControl.lh){
            lsu_in.bits.mem_ctl := MEM_CTL.Read2B
        }
        is(MemControl.lhu){
            lsu_in.bits.mem_ctl := MEM_CTL.Read2BU
        }
        is(MemControl.sb){
            lsu_in.bits.mem_ctl := MEM_CTL.Write1B
        }
        is(MemControl.sh){
            lsu_in.bits.mem_ctl := MEM_CTL.Write2B
        }
        is(MemControl.sw){
            lsu_in.bits.mem_ctl := MEM_CTL.Write4B
        }
    }


}