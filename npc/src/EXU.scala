package npc

import chisel3._
import chisel3.util._
import scala.reflect.internal.Reporter.INFO
import chisel3.simulator.PeekPokeAPI.TestableData
import npc.MemControl.sw

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

    val IDU_in = IO(Flipped(Decoupled(new Bundle {
        val DecodeBundle = new Bundle {
            val imm = Input(UInt(32.W))
            val rs1 = Input(UInt(32.W))
            val rs2 = Input(UInt(32.W))
            val rd = Input(UInt(32.W))
            val pc = Input(UInt(32.W))
        }
        
        val EXU_CTL = new EXUCTLBundle
    })))
    val WriteBackPort = IO(Decoupled(new WriteBackPortBundle))

    val MemPort = IO(Decoupled(new MemPortBundle))
    val MemReadData = IO(Flipped(Decoupled(new Bundle {
        val rdata = Input(UInt(32.W))
    })))

    // io.out := 0.U
    val AluResult = WireDefault(0.U(32.W))
    val PCAddFour = IDU_in.bits.DecodeBundle.pc + 4.U
    
    IDU_in.ready := true.B
    MemReadData.ready := true.B

    // val IDU_in_valid = RegInit(false.B)

    val isLoad = WireInit(false.B)

    RFS_ReadPort.RegReadPort.raddr1 := 0.U
    RFS_ReadPort.RegReadPort.raddr2 := 0.U
    val src1 = WireDefault(0.U(32.W))
    switch(IDU_in.bits.EXU_CTL.ALU_Src1){
        is(ALUSrc1.rs1){
            RFS_ReadPort.RegReadPort.raddr1 := IDU_in.bits.DecodeBundle.rs1
            src1 := RFS_ReadPort.RegReadPort.rdata1
        }
        is(ALUSrc1.pc){
            src1 := IDU_in.bits.DecodeBundle.pc
        }
    }
    val src2 = WireDefault(0.U(32.W))
    switch(IDU_in.bits.EXU_CTL.ALU_Src2){
        is(ALUSrc2.rs2){
            RFS_ReadPort.RegReadPort.raddr2 := IDU_in.bits.DecodeBundle.rs2
            src2 := RFS_ReadPort.RegReadPort.rdata2
        }
        is(ALUSrc2.imm){
            src2 := IDU_in.bits.DecodeBundle.imm
        }
        is(ALUSrc2.shamt){
            src2 := Cat(0.U(27.W),IDU_in.bits.DecodeBundle.imm(4,0))
        }
        is(ALUSrc2.csr){
            RFS_ReadPort.CSRReadPort.CSRRAddr := IDU_in.bits.DecodeBundle.imm(11,0)
            src2 := RFS_ReadPort.CSRReadPort.CSRRData
        }
    }

    switch(IDU_in.bits.EXU_CTL.ALU_Control){
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
            AluResult := IDU_in.bits.DecodeBundle.imm
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
    WriteBackPort.bits := 0.U.asTypeOf(new WriteBackPortBundle)
    when(IDU_in.bits.EXU_CTL.ALUWrite2Reg){
        WriteBackPort.bits.RegWaddr := IDU_in.bits.DecodeBundle.rd
        WriteBackPort.bits.RegWrite := true.B
        WriteBackPort.bits.RegWdata := AluResult
    }
    RFS_ReadPort.CSRReadPort.CSRRAddr := 0.U
    switch(IDU_in.bits.EXU_CTL.CSR_Contorl){
        is(CSRControl.ecall){
            WriteBackPort.bits.Exception_Code := 11.U
            WriteBackPort.bits.PC := IDU_in.bits.DecodeBundle.pc
            WriteBackPort.bits.isTrap := true.B
        }
        is(CSRControl.mret){
            RFS_ReadPort.CSRReadPort.CSRRAddr := "h341".U
            WriteBackPort.bits.PCNext := RFS_ReadPort.CSRReadPort.CSRRData
            WriteBackPort.bits.PCWrite := true.B
        }
        is(CSRControl.csrrs){
            RFS_ReadPort.CSRReadPort.CSRRAddr := IDU_in.bits.DecodeBundle.imm(11,0)
            WriteBackPort.bits.RegWaddr := IDU_in.bits.DecodeBundle.rd
            WriteBackPort.bits.RegWdata := RFS_ReadPort.CSRReadPort.CSRRData
            WriteBackPort.bits.RegWrite := true.B
            WriteBackPort.bits.CSRAddr := IDU_in.bits.DecodeBundle.imm(11,0)
            WriteBackPort.bits.CSRData := AluResult
            WriteBackPort.bits.CSRWrite := true.B
        }
        is(CSRControl.csrrw){
            RFS_ReadPort.RegReadPort.raddr1 := IDU_in.bits.DecodeBundle.rs1
            RFS_ReadPort.CSRReadPort.CSRRAddr := IDU_in.bits.DecodeBundle.imm(11,0)
            WriteBackPort.bits.RegWaddr := IDU_in.bits.DecodeBundle.rd
            WriteBackPort.bits.RegWdata := RFS_ReadPort.CSRReadPort.CSRRData
            WriteBackPort.bits.RegWrite := true.B
            WriteBackPort.bits.CSRAddr := IDU_in.bits.DecodeBundle.imm(11,0)
            WriteBackPort.bits.CSRData := RFS_ReadPort.RegReadPort.rdata1
            WriteBackPort.bits.CSRWrite := true.B
        }
    }
    
    switch(IDU_in.bits.EXU_CTL.ALU_Jump){
        is(Jump.jal){
            WriteBackPort.bits.PCWrite := true.B
            WriteBackPort.bits.PCNext := AluResult
            WriteBackPort.bits.RegWaddr := IDU_in.bits.DecodeBundle.rd
            WriteBackPort.bits.RegWrite := true.B
            WriteBackPort.bits.RegWdata := PCAddFour
        }
    }

    val BranchCond = WireDefault(false.B)
    switch(IDU_in.bits.EXU_CTL.Branch_Control){
        is(BranchControl.beq){
            RFS_ReadPort.RegReadPort.raddr1 := IDU_in.bits.DecodeBundle.rs1
            RFS_ReadPort.RegReadPort.raddr2 := IDU_in.bits.DecodeBundle.rs2
            BranchCond := RFS_ReadPort.RegReadPort.rdata1 === RFS_ReadPort.RegReadPort.rdata2
        }
        is(BranchControl.bne){
            RFS_ReadPort.RegReadPort.raddr1 := IDU_in.bits.DecodeBundle.rs1
            RFS_ReadPort.RegReadPort.raddr2 := IDU_in.bits.DecodeBundle.rs2
            BranchCond := RFS_ReadPort.RegReadPort.rdata1 =/= RFS_ReadPort.RegReadPort.rdata2
        }
        is(BranchControl.blt){
            RFS_ReadPort.RegReadPort.raddr1 := IDU_in.bits.DecodeBundle.rs1
            RFS_ReadPort.RegReadPort.raddr2 := IDU_in.bits.DecodeBundle.rs2
            BranchCond := RFS_ReadPort.RegReadPort.rdata1.asSInt < RFS_ReadPort.RegReadPort.rdata2.asSInt
        }
        is(BranchControl.bge){
            RFS_ReadPort.RegReadPort.raddr1 := IDU_in.bits.DecodeBundle.rs1
            RFS_ReadPort.RegReadPort.raddr2 := IDU_in.bits.DecodeBundle.rs2
            BranchCond := RFS_ReadPort.RegReadPort.rdata1.asSInt >= RFS_ReadPort.RegReadPort.rdata2.asSInt
        }
        is(BranchControl.bltu){
            RFS_ReadPort.RegReadPort.raddr1 := IDU_in.bits.DecodeBundle.rs1
            RFS_ReadPort.RegReadPort.raddr2 := IDU_in.bits.DecodeBundle.rs2
            BranchCond := RFS_ReadPort.RegReadPort.rdata1 < RFS_ReadPort.RegReadPort.rdata2
        }
        is(BranchControl.bgeu){
            RFS_ReadPort.RegReadPort.raddr1 := IDU_in.bits.DecodeBundle.rs1
            RFS_ReadPort.RegReadPort.raddr2 := IDU_in.bits.DecodeBundle.rs2
            BranchCond := RFS_ReadPort.RegReadPort.rdata1 >= RFS_ReadPort.RegReadPort.rdata2
        }
    }
    when(BranchCond){
        WriteBackPort.bits.PCWrite := true.B
        WriteBackPort.bits.PCNext := AluResult
        WriteBackPort.bits.RegWrite := false.B
    }

    MemPort.bits := 0.U.asTypeOf(new MemPortBundle)
    when(IDU_in.bits.EXU_CTL.Mem_Control =/= MemControl.none){
        MemPort.bits.valid := true.B
    }
    when(IDU_in.bits.EXU_CTL.Mem_Control.isOneOf(MemControl.lb,MemControl.lbu,MemControl.lh,
        MemControl.lhu,MemControl.lw)){
        MemPort.bits.raddr := AluResult
        WriteBackPort.bits.RegWaddr := IDU_in.bits.DecodeBundle.rd
        WriteBackPort.bits.RegWrite := true.B
        WriteBackPort.bits.RegWdata := MemReadData.bits.rdata
        isLoad := true.B
    }.elsewhen(IDU_in.bits.EXU_CTL.Mem_Control.isOneOf(MemControl.sb,MemControl.sh,MemControl.sw)){
        
        MemPort.bits.waddr := AluResult
        RFS_ReadPort.RegReadPort.raddr2 := IDU_in.bits.DecodeBundle.rs2
        MemPort.bits.wdata := RFS_ReadPort.RegReadPort.rdata2
    }
    switch(IDU_in.bits.EXU_CTL.Mem_Control){
        is(MemControl.lw){
            MemPort.bits.mem_ctl := MEM_CTL.Read4B
        }
        is(MemControl.lbu){
            MemPort.bits.mem_ctl := MEM_CTL.Read1BU
        }
        is(MemControl.lb){
            MemPort.bits.mem_ctl := MEM_CTL.Read1B
        }
        is(MemControl.lh){
            MemPort.bits.mem_ctl := MEM_CTL.Read2B
        }
        is(MemControl.lhu){
            MemPort.bits.mem_ctl := MEM_CTL.Read2BU
        }
        is(MemControl.sb){
            MemPort.bits.mem_ctl := MEM_CTL.Write1B
        }
        is(MemControl.sh){
            MemPort.bits.mem_ctl := MEM_CTL.Write2B
        }
        is(MemControl.sw){
            MemPort.bits.mem_ctl := MEM_CTL.Write4B
        }
    }
    val cycle =RegInit(0.U(32.W))
    cycle := cycle + 1.U

    val s_wait_idu :: s_wait_mem :: s_exec :: Nil = Enum(3)
    val status = RegInit(s_wait_idu)
    switch(status){
        is(s_wait_idu){
            when(IDU_in.fire){
                when(isLoad){
                    status := s_wait_mem
                }.otherwise{
                    status := s_wait_idu
                }
            }
        }
        // is(s_exec){
        //     when(WriteBackPort.fire){
        //         status := s_wait_idu
        //     }
        // }
        is(s_wait_mem){
            when(MemReadData.fire){
                status := s_wait_idu
            }
        }
    }


    WriteBackPort.valid := (isLoad && MemReadData.fire) || (!isLoad && IDU_in.fire)
    //printf("cycle: %x IDU_in.valid: %x isLoad: %x MemReadData.valid: %x valid: %x\n",cycle,IDU_in.valid,isLoad,MemReadData.valid,WriteBackPort.valid)
    MemPort.valid := true.B 
}