package npc

import chisel3._
import chisel3.util._
import scala.reflect.internal.Reporter.INFO
import chisel3.simulator.PeekPokeAPI.TestableData

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
    FOUR = Value
}

object ALUContorl extends ChiselEnum{
    val none,
    add,
    sub,
    and,
    or,
    lui,
    sll,
    srl,
    sra,
    slt,
    sltu,
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

class EXUCTLBundle extends Bundle{
    val ALU_Op = Output(ALUOp())
    val ALU_Src1 = Output(ALUSrc1())
    val ALU_Src2 = Output(ALUSrc2())
    val ALU_Control = Output(ALUContorl())
    val ALUWrite2Reg = Output(Bool())
    val ALU_Jump = Output(Jump())
    val Mem_Control = Output(MemControl())
    val Branch_Control = Output(BranchControl())
}

class EXU extends Module{

    val RegReadPort = IO(new Bundle {
        val raddr1 = Output(UInt(5.W))
        val rdata1 = Input(UInt(32.W))
        val raddr2 = Output(UInt(5.W))
        val rdata2 = Input(UInt(32.W))
    })
    val in = IO(new Bundle {
        val DecodeBundle = new Bundle {
            val imm = Input(UInt(32.W))
            val rs1 = Input(UInt(32.W))
            val rs2 = Input(UInt(32.W))
            val rd = Input(UInt(32.W))
            val pc = Input(UInt(32.W))
        }
        
        val EXU_CTL = Flipped(new EXUCTLBundle)
    })
    val WriteBackPort = IO(new WriteBackPortBundle)

    val MemPort = IO(new MemPortBundle)
    val MemReadData = IO(new Bundle {
        val rdata = Input(UInt(32.W))
    })

    // io.out := 0.U
    val AluResult = WireDefault(0.U(32.W))
    val PCAddFour = in.DecodeBundle.pc + 4.U

    RegReadPort.raddr1 := 0.U
    RegReadPort.raddr2 := 0.U
    val src1 = WireDefault(0.U(32.W))
    switch(in.EXU_CTL.ALU_Src1){
        is(ALUSrc1.rs1){
            RegReadPort.raddr1 := in.DecodeBundle.rs1
            src1 := RegReadPort.rdata1
        }
        is(ALUSrc1.pc){
            src1 := in.DecodeBundle.pc
        }
    }
    val src2 = WireDefault(0.U(32.W))
    switch(in.EXU_CTL.ALU_Src2){
        is(ALUSrc2.rs2){
            RegReadPort.raddr2 := in.DecodeBundle.rs2
            src2 := RegReadPort.rdata2
        }
        is(ALUSrc2.imm){
            src2 := in.DecodeBundle.imm
        }
        is(ALUSrc2.shamt){
            src2 := Cat(0.U(27.W),in.DecodeBundle.imm(4,0))
        }
    }

    switch(in.EXU_CTL.ALU_Control){
        is(ALUContorl.add){
            AluResult := src1 + src2
            printf(cf"aluResult: $AluResult\n")
        }
        is(ALUContorl.sub){
            AluResult := src1 - src2
        }
        is(ALUContorl.sltu){
            AluResult := src1 < src2
        }
        is(ALUContorl.and){
            AluResult := src1 & src2
        }
        is(ALUContorl.equ){
            AluResult := src1 === src2
        }
        is(ALUContorl.lui){
            AluResult := in.DecodeBundle.imm
        }
    }
    WriteBackPort := 0.U.asTypeOf(new WriteBackPortBundle)
    when(in.EXU_CTL.ALUWrite2Reg){
        WriteBackPort.RegWaddr := in.DecodeBundle.rd
        WriteBackPort.RegWrite := true.B
        WriteBackPort.RegWdata := AluResult
        // printf(cf"regWdata: $AluResult\n")
    }
    
    switch(in.EXU_CTL.ALU_Jump){
        is(Jump.jal){
            WriteBackPort.JumpOrBranch := true.B
            WriteBackPort.PCNext := AluResult
            WriteBackPort.RegWaddr := in.DecodeBundle.rd
            WriteBackPort.RegWrite := true.B
            WriteBackPort.RegWdata := PCAddFour
        }
    }

    val BranchCond = WireDefault(false.B)
    switch(in.EXU_CTL.Branch_Control){
        is(BranchControl.beq){
            BranchCond := in.DecodeBundle.rs1 === in.DecodeBundle.rs2
        }
    }
    when(BranchCond){
        WriteBackPort.JumpOrBranch := true.B
        WriteBackPort.PCNext := AluResult
        WriteBackPort.RegWaddr := in.DecodeBundle.rd
        WriteBackPort.RegWrite := true.B
        WriteBackPort.RegWdata := PCAddFour
    }

    MemPort := 0.U.asTypeOf(new MemPortBundle)
    when(in.EXU_CTL.Mem_Control =/= MemControl.none){
        MemPort.valid := true.B
    }
    when(in.EXU_CTL.Mem_Control.isOneOf(MemControl.lb,MemControl.lbu,MemControl.lh,
        MemControl.lhu,MemControl.lw)){
        MemPort.raddr := AluResult
        WriteBackPort.RegWaddr := in.DecodeBundle.rd
        WriteBackPort.RegWrite := true.B
        WriteBackPort.RegWdata := MemReadData.rdata
    }.elsewhen(in.EXU_CTL.Mem_Control.isOneOf(MemControl.sb,MemControl.sh,MemControl.sw)){
        
        MemPort.waddr := AluResult
        RegReadPort.raddr1 := in.DecodeBundle.rs2
        MemPort.wdata := RegReadPort.rdata1
        printf(cf"memwdata: ${MemPort.wdata} memwaddr: ${MemPort.waddr}\n")
    }
    switch(in.EXU_CTL.Mem_Control){
        is(MemControl.lw){
            MemPort.mem_ctl := MEM_CTL.Read4B
        }
        is(MemControl.lbu){
            MemPort.mem_ctl := MEM_CTL.Read1BU
        }
        is(MemControl.lb){
            MemPort.mem_ctl := MEM_CTL.Read1B
        }
        is(MemControl.sb){
            MemPort.mem_ctl := MEM_CTL.Write1B
        }
        is(MemControl.sw){
            MemPort.mem_ctl := MEM_CTL.Write4B
        }
    }

}