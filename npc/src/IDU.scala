package npc

import chisel3._
import chisel3.util._

    // import "DPI-C" function void ebreak();
    // import "DPI-C" function void invalid_instruction(input int pc,input int inst);
 
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
        val ALU_ctl = Output(UInt(4.W))

        val WB_sel = Output(UInt(3.W))
        val reg_waddr = Output(UInt(32.W))
        val reg_we = Output(Bool())

        val pc_sel = Output(UInt(2.W))
        val pc_we = Output(Bool())

        val mem_ctl = Output(UInt(4.W))
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

    val opcode = WireDefault(io.inst(6,0))
    val rd = WireDefault(io.inst(11,7))
    val funct3 = WireDefault(io.inst(14,12))
    val funct7 = WireDefault(io.inst(31,25))
    val immI = WireDefault(Cat(Fill(20,io.inst(31)),io.inst(31,20)))
    val immU = WireDefault(Cat(io.inst(31,12),0.U(12.W)))
    val immS = WireDefault(Cat(Fill(20,io.inst(31)),io.inst(31,25),io.inst(11,7)))

    val rs1_inst = WireDefault(io.inst(19,15))
    val rs2_inst = WireDefault(io.inst(24,20))

    io.rs1 := io.inst(19,15);
    io.rs2 := io.inst(24,20);

    io.imm := 0.U(32.W);
    io.src1 := 0.U(32.W);
    io.src2 := 0.U(32.W);
    io.ALU_ctl := 0.U(4.W);

    io.reg_waddr := 0.U(5.W);
    io.reg_we := false.B;
    io.WB_sel := 0.U(3.W);

    io.pc_sel := 0.U(2.W);
    io.pc_we := false.B;

    io.mem_ctl :=0.U(4.W);
    io.mem_wen := false.B;
    io.mem_valid := false.B;
    invalid := true.B

    when(reset.asBool){
        invalid := false.B
    }.otherwise{
        switch(opcode){
        is("b0010011".U){
            when(funct3 === "b000".U){// addi
            
                io.imm := immI;
                io.src1 := io.reg_rdata1;
                io.ALU_ctl :="b0001".U;
                io.reg_waddr := rd;
                io.reg_we := true.B;
                io.WB_sel := "b001".U;
                invalid := false.B
            }
        }
        is("b1100111".U){
            
            when(funct3 === "b000".U){//jalr
                io.imm := immI;
                io.src1 := io.reg_rdata1;
                io.ALU_ctl := "b0001".U;
                io.reg_waddr := rd;
                io.reg_we := true.B;
                io.pc_we := true.B;
                io.WB_sel := "b011".U;
                io.pc_sel := "b01".U;
                invalid := false.B
            }
        }
        is("b1110011".U){
            printf(p"ebreak0\n");
            when(funct3 === "b000".U){//ebreak
            printf(p"ebreak1\n");
                when(immI === 1.U 
                && rs1_inst === 0.U 
                && rd === 0.U
                ){
                    printf(p"ebreak2\n");
                    
                    invalid := false.B
                    trap := true.B
                }
            }
        }
        is("b0110011".U){// add
        
            when(funct3 === "b000".U && funct7 === "b0000000".U){
                io.src1 := io.reg_rdata1;
                io.src2 := io.reg_rdata2;
                io.ALU_ctl := "b0010".U;
                io.reg_waddr := rd;
                io.reg_we := true.B;
                io.WB_sel := 1.U(3.W);
                invalid := false.B
            }
        }
        is("b0110111".U){// lui
        
            io.imm := immU;
            io.reg_waddr := rd;
            io.reg_we := true.B;
            io.WB_sel := "b100".U;
            invalid := false.B
        }
        is("b0000011".U){
            when(funct3 === "b010".U){//lw
            
                io.imm := immI;
                io.src1 := io.reg_rdata1;
                io.ALU_ctl := "b0001".U;
                io.reg_waddr := rd;
                io.reg_we := true.B;
                io.WB_sel := "b010".U;
                io.mem_ctl := 1.U(4.W);
                io.mem_valid := true.B;
                invalid := false.B
            }.elsewhen(funct3 === "b100".U){//lbu
            
                io.imm := immI;
                io.src1 := io.reg_rdata1;
                io.reg_waddr := rd;
                io.reg_we := true.B;
                io.ALU_ctl := "b0001".U;
                io.WB_sel := "b010".U;
                io.mem_ctl := "b0011".U;
                io.mem_valid := true.B;
                invalid := false.B
            }
        }
        is("b0100011".U){
            when(funct3 === "b010".U){// sw
            
                io.imm := immS;
                io.src1 := io.reg_rdata1;
                io.src2 := io.reg_rdata2;
                io.ALU_ctl := "b0001".U;
                io.mem_ctl := "b0100".U;
                io.mem_valid := true.B;
                io.mem_wen := true.B;
                invalid := false.B
                
            }.elsewhen(funct3 === "b000".U){// sb
            
                io.imm := immS;
                io.src1 := io.reg_rdata1;
                io.src2 := io.reg_rdata2;
                io.ALU_ctl := "b0001".U;
                io.mem_ctl := "b0101".U;
                io.mem_valid := true.B;
                io.mem_wen := true.B;
                invalid := false.B
                
            }
        }
        }
    }
}