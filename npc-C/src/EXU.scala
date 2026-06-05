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

    val IDU_in = IO(new Bundle {
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
    val PCAddFour = IDU_in.DecodeBundle.pc + 4.U

    RFS_ReadPort.RegReadPort.raddr1 := 0.U
    RFS_ReadPort.RegReadPort.raddr2 := 0.U
    val src1 = WireDefault(0.U(32.W))
    switch(IDU_in.EXU_CTL.ALU_Src1){
        is(ALUSrc1.rs1){
            RFS_ReadPort.RegReadPort.raddr1 := IDU_in.DecodeBundle.rs1
            src1 := RFS_ReadPort.RegReadPort.rdata1
        }
        is(ALUSrc1.pc){
            src1 := IDU_in.DecodeBundle.pc
        }
    }
    val src2 = WireDefault(0.U(32.W))
    switch(IDU_in.EXU_CTL.ALU_Src2){
        is(ALUSrc2.rs2){
            RFS_ReadPort.RegReadPort.raddr2 := IDU_in.DecodeBundle.rs2
            src2 := RFS_ReadPort.RegReadPort.rdata2
        }
        is(ALUSrc2.imm){
            src2 := IDU_in.DecodeBundle.imm
        }
        is(ALUSrc2.shamt){
            src2 := Cat(0.U(27.W),IDU_in.DecodeBundle.imm(4,0))
        }
        is(ALUSrc2.csr){
            RFS_ReadPort.CSRReadPort.CSRRAddr := IDU_in.DecodeBundle.imm(11,0)
            src2 := RFS_ReadPort.CSRReadPort.CSRRData
        }
    }

    switch(IDU_in.EXU_CTL.ALU_Control){
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
            AluResult := IDU_in.DecodeBundle.imm
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
    WriteBackPort := 0.U.asTypeOf(new WriteBackPortBundle)
    when(IDU_in.EXU_CTL.ALUWrite2Reg){
        WriteBackPort.RegWaddr := IDU_in.DecodeBundle.rd
        WriteBackPort.RegWrite := true.B
        WriteBackPort.RegWdata := AluResult
    }
    RFS_ReadPort.CSRReadPort.CSRRAddr := 0.U
    switch(IDU_in.EXU_CTL.CSR_Contorl){
        is(CSRControl.ecall){
            WriteBackPort.Exception_Code := 11.U
            WriteBackPort.PC := IDU_in.DecodeBundle.pc
            WriteBackPort.isTrap := true.B
        }
        is(CSRControl.mret){
            RFS_ReadPort.CSRReadPort.CSRRAddr := "h341".U
            WriteBackPort.PCNext := RFS_ReadPort.CSRReadPort.CSRRData
            WriteBackPort.PCWrite := true.B
        }
        is(CSRControl.csrrs){
            RFS_ReadPort.CSRReadPort.CSRRAddr := IDU_in.DecodeBundle.imm(11,0)
            WriteBackPort.RegWaddr := IDU_in.DecodeBundle.rd
            WriteBackPort.RegWdata := RFS_ReadPort.CSRReadPort.CSRRData
            WriteBackPort.RegWrite := true.B
            WriteBackPort.CSRAddr := IDU_in.DecodeBundle.imm(11,0)
            WriteBackPort.CSRData := AluResult
            WriteBackPort.CSRWrite := true.B
        }
        is(CSRControl.csrrw){
            RFS_ReadPort.RegReadPort.raddr1 := IDU_in.DecodeBundle.rs1
            RFS_ReadPort.CSRReadPort.CSRRAddr := IDU_in.DecodeBundle.imm(11,0)
            WriteBackPort.RegWaddr := IDU_in.DecodeBundle.rd
            WriteBackPort.RegWdata := RFS_ReadPort.CSRReadPort.CSRRData
            WriteBackPort.RegWrite := true.B
            WriteBackPort.CSRAddr := IDU_in.DecodeBundle.imm(11,0)
            WriteBackPort.CSRData := RFS_ReadPort.RegReadPort.rdata1
            WriteBackPort.CSRWrite := true.B
        }
    }
    
    switch(IDU_in.EXU_CTL.ALU_Jump){
        is(Jump.jal){
            WriteBackPort.PCWrite := true.B
            WriteBackPort.PCNext := AluResult
            WriteBackPort.RegWaddr := IDU_in.DecodeBundle.rd
            WriteBackPort.RegWrite := true.B
            WriteBackPort.RegWdata := PCAddFour
        }
    }

    val BranchCond = WireDefault(false.B)
    switch(IDU_in.EXU_CTL.Branch_Control){
        is(BranchControl.beq){
            RFS_ReadPort.RegReadPort.raddr1 := IDU_in.DecodeBundle.rs1
            RFS_ReadPort.RegReadPort.raddr2 := IDU_in.DecodeBundle.rs2
            BranchCond := RFS_ReadPort.RegReadPort.rdata1 === RFS_ReadPort.RegReadPort.rdata2
        }
        is(BranchControl.bne){
            RFS_ReadPort.RegReadPort.raddr1 := IDU_in.DecodeBundle.rs1
            RFS_ReadPort.RegReadPort.raddr2 := IDU_in.DecodeBundle.rs2
            BranchCond := RFS_ReadPort.RegReadPort.rdata1 =/= RFS_ReadPort.RegReadPort.rdata2
        }
        is(BranchControl.blt){
            RFS_ReadPort.RegReadPort.raddr1 := IDU_in.DecodeBundle.rs1
            RFS_ReadPort.RegReadPort.raddr2 := IDU_in.DecodeBundle.rs2
            BranchCond := RFS_ReadPort.RegReadPort.rdata1.asSInt < RFS_ReadPort.RegReadPort.rdata2.asSInt
        }
        is(BranchControl.bge){
            RFS_ReadPort.RegReadPort.raddr1 := IDU_in.DecodeBundle.rs1
            RFS_ReadPort.RegReadPort.raddr2 := IDU_in.DecodeBundle.rs2
            BranchCond := RFS_ReadPort.RegReadPort.rdata1.asSInt >= RFS_ReadPort.RegReadPort.rdata2.asSInt
        }
        is(BranchControl.bltu){
            RFS_ReadPort.RegReadPort.raddr1 := IDU_in.DecodeBundle.rs1
            RFS_ReadPort.RegReadPort.raddr2 := IDU_in.DecodeBundle.rs2
            BranchCond := RFS_ReadPort.RegReadPort.rdata1 < RFS_ReadPort.RegReadPort.rdata2
        }
        is(BranchControl.bgeu){
            RFS_ReadPort.RegReadPort.raddr1 := IDU_in.DecodeBundle.rs1
            RFS_ReadPort.RegReadPort.raddr2 := IDU_in.DecodeBundle.rs2
            BranchCond := RFS_ReadPort.RegReadPort.rdata1 >= RFS_ReadPort.RegReadPort.rdata2
        }
    }
    when(BranchCond){
        WriteBackPort.PCWrite := true.B
        WriteBackPort.PCNext := AluResult
        WriteBackPort.RegWrite := false.B
    }

    MemPort := 0.U.asTypeOf(new MemPortBundle)
    when(IDU_in.EXU_CTL.Mem_Control =/= MemControl.none){
        MemPort.valid := true.B
    }
    when(IDU_in.EXU_CTL.Mem_Control.isOneOf(MemControl.lb,MemControl.lbu,MemControl.lh,
        MemControl.lhu,MemControl.lw)){
        MemPort.raddr := AluResult
        WriteBackPort.RegWaddr := IDU_in.DecodeBundle.rd
        WriteBackPort.RegWrite := true.B
        WriteBackPort.RegWdata := MemReadData.rdata
    }.elsewhen(IDU_in.EXU_CTL.Mem_Control.isOneOf(MemControl.sb,MemControl.sh,MemControl.sw)){
        
        MemPort.waddr := AluResult
        RFS_ReadPort.RegReadPort.raddr2 := IDU_in.DecodeBundle.rs2
        MemPort.wdata := RFS_ReadPort.RegReadPort.rdata2
    }
    switch(IDU_in.EXU_CTL.Mem_Control){
        is(MemControl.lw){
            MemPort.mem_ctl := MEM_CTL.Read4B
        }
        is(MemControl.lbu){
            MemPort.mem_ctl := MEM_CTL.Read1BU
        }
        is(MemControl.lb){
            MemPort.mem_ctl := MEM_CTL.Read1B
        }
        is(MemControl.lh){
            MemPort.mem_ctl := MEM_CTL.Read2B
        }
        is(MemControl.lhu){
            MemPort.mem_ctl := MEM_CTL.Read2BU
        }
        is(MemControl.sb){
            MemPort.mem_ctl := MEM_CTL.Write1B
        }
        is(MemControl.sh){
            MemPort.mem_ctl := MEM_CTL.Write2B
        }
        is(MemControl.sw){
            MemPort.mem_ctl := MEM_CTL.Write4B
        }
    }

}