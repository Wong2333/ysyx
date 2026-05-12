package npc

import chisel3._
import chisel3.util._
import npc.ALU_CTL
import npc.WB_CTL
import npc.PC_CTL

class InstDecode extends Bundle{
    val inst = UInt(32.W)
    def opcode = inst(6,0)
    def rs1 = inst(19,15)
    def rs2 = inst(24,20)
    def rd = inst(11,7)
    def funct3 = inst(14,12)
    def funct7 = inst(31,25)
    def immI = Cat(Fill(20,inst(31)),inst(31,20))
    def immB = Cat(Fill(19, inst(31)), inst(31), inst(7), inst(30,25), inst(11,8), 0.U(1.W))
    def immU = Cat(inst(31,12),0.U(12.W))
    def immS = Cat(Fill(20,inst(31)),inst(31,25),inst(11,7))
    def immJ = Cat(Fill(11, inst(31)), inst(31), inst(19,12), inst(20), inst(30,21), 0.U(1.W))
    
}

 
class EbreakBlackBox extends ExtModule{
    val io = FlatIO(new Bundle{
        val trap = Input(Bool())
    })
    addResource("/EbreakBlackBox.v")
}
class InvalidInstructionBlackBox extends ExtModule{
    val io = FlatIO(new Bundle{
        val pc = Input(UInt(32.W))
        val inst = Input(UInt(32.W))
        val invalid = Input(Bool())
    })
    addResource("/InvalidInstructionBlackBox.v")
}


class IDU extends Module{
    val io = IO(new Bundle {
        val pc = Input(UInt(32.W))
        val inst = Input(UInt(32.W))
        val reg_rdata1 = Input(UInt(32.W))
        val reg_rdata2 = Input(UInt(32.W))

        val rs1 = Output(UInt(32.W))
        val rs2 = Output(UInt(32.W))

        val imm = Output(UInt(32.W))
        val src1 = Output(UInt(32.W))
        val src2 = Output(UInt(32.W))
        val ALU_ctl = Output(ALU_CTL())

        val WB_sel = Output(WB_CTL())
        val reg_waddr = Output(UInt(32.W))
        val reg_we = Output(Bool())

        val pc_sel = Output(PC_CTL())
        val pc_we = Output(Bool())

        val mem_ctl = Output(MEM_CTL())
        val mem_wen = Output(Bool())
        val mem_valid = Output(Bool())
    })
    val trap = RegInit(false.B)
    val invalid = WireDefault(false.B)

    val ebreak = Module(new EbreakBlackBox())
    ebreak.io.trap := trap
    

    val invalid_instruction = Module(new InvalidInstructionBlackBox())
    invalid_instruction.io.pc := io.pc
    invalid_instruction.io.inst := io.inst
    invalid_instruction.io.invalid := invalid

    val inst = io.inst.asTypeOf(new InstDecode)

    io.rs1 := io.inst(19,15);
    io.rs2 := io.inst(24,20);

    io.imm := 0.U(32.W);
    io.src1 := 0.U(32.W);
    io.src2 := 0.U(32.W);
    io.ALU_ctl := ALU_CTL.none;

    io.reg_waddr := 0.U(5.W);
    io.reg_we := false.B;
    io.WB_sel := WB_CTL.none;

    io.pc_sel := PC_CTL.none;
    io.pc_we := false.B;

    io.mem_ctl := MEM_CTL.none;
    io.mem_wen := false.B;
    io.mem_valid := false.B;
    invalid := true.B

    when(reset.asBool){
        invalid := false.B
    }.otherwise{
        switch(inst.opcode){
        is("b0010011".U){
            when(inst.funct3 === "b000".U){// addi
                io.imm := inst.immI;
                io.src1 := io.reg_rdata1;
                io.ALU_ctl := ALU_CTL.src1AddImm;
                io.reg_waddr := inst.rd;
                io.reg_we := true.B;
                io.WB_sel := WB_CTL.ALU2Reg;
                invalid := false.B
            }
        }
        is("b1100111".U){
            when(inst.funct3 === "b000".U){//jalr
                io.imm := inst.immI;
                io.src1 := io.reg_rdata1;
                io.ALU_ctl := ALU_CTL.src1AddImm;
                io.reg_waddr := inst.rd;
                io.reg_we := true.B;
                io.pc_we := true.B;
                io.WB_sel := WB_CTL.PCNext2Reg;
                io.pc_sel := PC_CTL.ALU2PC;
                invalid := false.B
            }
        }
        is("b1110011".U){
            when(inst.funct3 === "b000".U){//ebreak
                when(inst.immI === 1.U 
                && inst.rs1 === 0.U 
                && inst.rd === 0.U
                ){
                    invalid := false.B
                    trap := true.B
                }
            }
        }
        is("b0110011".U){// add
        
            when(inst.funct3 === "b000".U && inst.funct7 === "b0000000".U){
                io.src1 := io.reg_rdata1;
                io.src2 := io.reg_rdata2;
                io.ALU_ctl := ALU_CTL.src1AddSrc2;
                io.reg_waddr := inst.rd;
                io.reg_we := true.B;
                io.WB_sel := WB_CTL.ALU2Reg;
                invalid := false.B
            }
        }
        is("b0110111".U){// lui
        
            io.imm := inst.immU;
            io.reg_waddr := inst.rd;
            io.reg_we := true.B;
            io.WB_sel := WB_CTL.Imm2Reg;
            invalid := false.B
        }
        is("b0010111".U){// auipc
            io.imm := inst.immU
            io.reg_waddr := inst.rd
            io.reg_we := true.B
            io.src1 := io.pc
            io.ALU_ctl := ALU_CTL.src1AddImm
            io.WB_sel := WB_CTL.ALU2Reg
            invalid := false.B
        }
        is("b0000011".U){
            when(inst.funct3 === "b010".U){//lw
            
                io.imm := inst.immI;
                io.src1 := io.reg_rdata1;
                io.ALU_ctl := ALU_CTL.src1AddImm;
                io.reg_waddr := inst.rd;
                io.reg_we := true.B;
                io.WB_sel := WB_CTL.Mem2Reg;
                io.mem_ctl := MEM_CTL.Read4B;
                io.mem_valid := true.B;
                invalid := false.B
            }.elsewhen(inst.funct3 === "b100".U){//lbu
            
                io.imm := inst.immI;
                io.src1 := io.reg_rdata1;
                io.reg_waddr := inst.rd;
                io.reg_we := true.B;
                io.ALU_ctl := ALU_CTL.src1AddImm;
                io.WB_sel := WB_CTL.Mem2Reg;
                io.mem_ctl := MEM_CTL.Read1BU;
                io.mem_valid := true.B;
                invalid := false.B
            }
        }
        is("b0100011".U){
            when(inst.funct3 === "b010".U){// sw
            
                io.imm := inst.immS;
                io.src1 := io.reg_rdata1;
                io.src2 := io.reg_rdata2;
                io.ALU_ctl := ALU_CTL.src1AddImm;
                io.mem_ctl := MEM_CTL.Write4B;
                io.mem_valid := true.B;
                io.mem_wen := true.B;
                invalid := false.B
                
            }.elsewhen(inst.funct3 === "b000".U){// sb
            
                io.imm := inst.immS;
                io.src1 := io.reg_rdata1;
                io.src2 := io.reg_rdata2;
                io.ALU_ctl := ALU_CTL.src1AddImm;
                io.mem_ctl := MEM_CTL.Write1B;
                io.mem_valid := true.B;
                io.mem_wen := true.B;
                invalid := false.B
                
            }
        }
        }
    }
}