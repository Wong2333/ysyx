package npc

import chisel3._
import chisel3.util._

class InstDecode extends Bundle{
    val inst = UInt(32.W)
    def opcode = inst(6,0)
    def rs1 = inst(19,15)
    def rs2 = inst(24,20)
    def rd = inst(11,7)
    def funct3 = inst(14,12)
    def funct7 = inst(31,25)
    def immI = Cat(Fill(20,inst(31)),inst(31,20))
    def immIU = Cat(Fill(20,0.U),inst(31,20))
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
    val in = IO(new Bundle {
        val pc = Input(UInt(32.W))
        val inst = Input(UInt(32.W))
    })

    val out = IO(new Bundle {
        val DecodeBundle = new Bundle {
            val rs1 = Output(UInt(5.W))
            val rs2 = Output(UInt(5.W))
            val rd = Output(UInt(5.W))
            val imm = Output(UInt(32.W))
            val pc = Output(UInt(32.W))
        }
        val EXU_CTL = new EXUCTLBundle
    })
    val trap = RegInit(false.B)
    val invalid = WireDefault(false.B)

    val ebreak = Module(new EbreakBlackBox())
    ebreak.io.trap := trap
    

    val invalid_instruction = Module(new InvalidInstructionBlackBox())
    invalid_instruction.io.pc := in.pc
    invalid_instruction.io.inst := in.inst
    invalid_instruction.io.invalid := invalid

    val inst = in.inst.asTypeOf(new InstDecode)

    out.DecodeBundle.rs1 := inst.rs1
    out.DecodeBundle.rs2 := inst.rs2
    out.DecodeBundle.rd := inst.rd
    out.DecodeBundle.pc := in.pc
    out.DecodeBundle.imm := DontCare

    out.EXU_CTL.ALU_Op := DontCare
    out.EXU_CTL.ALU_Control := DontCare
    out.EXU_CTL.ALU_Src1 := DontCare
    out.EXU_CTL.ALU_Src2 := DontCare
    out.EXU_CTL.ALU_Jump := Jump.none
    out.EXU_CTL.Mem_Control := MemControl.none
    out.EXU_CTL.Branch_Control := BranchControl.none
    out.EXU_CTL.ALUWrite2Reg := false.B
    out.EXU_CTL.CSR_Contorl := CSRControl.none

    invalid := true.B

    when(reset.asBool){
        invalid := false.B
    }.otherwise{
        switch(inst.opcode){
        is("b0010011".U){
            out.EXU_CTL.ALU_Op := ALUOp.IType
            out.DecodeBundle.imm := inst.immI
            out.EXU_CTL.ALU_Src1 := ALUSrc1.rs1
            out.EXU_CTL.ALU_Src2 := ALUSrc2.imm
            out.EXU_CTL.ALUWrite2Reg := true.B
            when(inst.funct3 === "b000".U){// addi
                out.EXU_CTL.ALU_Control := ALUContorl.add
                invalid := false.B
            }
            when(inst.funct3 === "b011".U){// sltiu
                out.EXU_CTL.ALU_Control := ALUContorl.sltu
                invalid := false.B
            }
            when(inst.funct3 === "b010".U){// slti
                out.EXU_CTL.ALU_Control := ALUContorl.slt
                invalid := false.B
            }
            when(inst.funct3 === "b001".U && inst.immI(11,5) === "b0".U ){// slli
                out.EXU_CTL.ALU_Control := ALUContorl.sll
                out.EXU_CTL.ALU_Src2 := ALUSrc2.shamt
                invalid := false.B
            }
            when(inst.funct3 === "b101".U && inst.immI(11,5) === "b0".U){//srli
                out.EXU_CTL.ALU_Control := ALUContorl.srl
                out.EXU_CTL.ALU_Src2 := ALUSrc2.shamt
                invalid := false.B   
            }
            when(inst.funct3 === "b101".U && inst.immI(11,5) === "b0100000".U){//srai
                out.EXU_CTL.ALU_Control := ALUContorl.sra
                out.EXU_CTL.ALU_Src2 := ALUSrc2.shamt
                invalid := false.B   
            }

            when(inst.funct3 === "b111".U){// andi
                out.EXU_CTL.ALU_Control := ALUContorl.and
                invalid := false.B
            }
            when(inst.funct3 === "b100".U){// xori
                out.EXU_CTL.ALU_Control := ALUContorl.xor
                invalid := false.B
            }
            when(inst.funct3 === "b110".U){// ori
                out.EXU_CTL.ALU_Control := ALUContorl.or
                invalid := false.B
            }
        }
        is("b1100111".U){
            out.EXU_CTL.ALU_Op := ALUOp.IType
            out.DecodeBundle.imm := inst.immI
            out.EXU_CTL.ALU_Src1 := ALUSrc1.rs1
            out.EXU_CTL.ALU_Src2 := ALUSrc2.imm
            when(inst.funct3 === "b000".U){//jalr
                out.EXU_CTL.ALU_Jump := Jump.jal
                out.EXU_CTL.ALU_Control := ALUContorl.add
                invalid := false.B
            }
        }
        is("b1110011".U){
            when(inst.funct3 === "b000".U && inst.immI === 1.U && 
                        inst.rs1 === 0.U && inst.rd === 0.U){//ebreak
                    invalid := false.B
                    trap := true.B
                
            }.elsewhen(inst.funct3 === "b010".U){// csrrs
                out.DecodeBundle.rd := inst.rd
                out.DecodeBundle.rs1 := inst.rs1
                out.DecodeBundle.imm := inst.immI
                out.EXU_CTL.ALU_Src1 := ALUSrc1.rs1
                out.EXU_CTL.ALU_Src2 := ALUSrc2.csr
                out.EXU_CTL.ALU_Control := ALUContorl.or
                out.EXU_CTL.CSR_Contorl := CSRControl.csrrs
                invalid := false.B
            }.elsewhen(inst.funct3 === "b001".U){// csrws
                out.DecodeBundle.rd := inst.rd
                out.DecodeBundle.rs1 := inst.rs1
                out.DecodeBundle.imm := inst.immI
                out.EXU_CTL.CSR_Contorl := CSRControl.csrrw
                invalid := false.B
            }.elsewhen(inst.funct3 === 0.U && inst.rd === 0.U && 
                    inst.rs1 === 0.U && inst.immI === "b001100000010".U){// mret
                    out.EXU_CTL.CSR_Contorl := CSRControl.mret
                    invalid := false.B
            }.elsewhen(inst.funct3 === "b000".U && inst.immI === 0.U && 
                        inst.rs1 === 0.U && inst.rd === 0.U){ // ecall
                out.EXU_CTL.CSR_Contorl := CSRControl.ecall
                out.DecodeBundle.pc := in.pc
                invalid := false.B
            }
        }
        is("b0110011".U){
            out.EXU_CTL.ALU_Op := ALUOp.RType
            out.EXU_CTL.ALU_Src1 := ALUSrc1.rs1
            out.EXU_CTL.ALU_Src2 := ALUSrc2.rs2
            out.EXU_CTL.ALUWrite2Reg := true.B
            when(inst.funct3 === "b000".U && inst.funct7 === "b0000000".U){// add
                out.EXU_CTL.ALU_Control := ALUContorl.add
                invalid := false.B
            }
            when(inst.funct3 === "b000".U && inst.funct7 === "b0100000".U){// sub
                out.EXU_CTL.ALU_Control := ALUContorl.sub
                invalid := false.B
            }
            when(inst.funct3 === "b111".U && inst.funct7 === "b0000000".U){// and
                out.EXU_CTL.ALU_Control := ALUContorl.and
                invalid := false.B
            }
            when(inst.funct3 === "b100".U && inst.funct7 === "b0000000".U){// xor
                out.EXU_CTL.ALU_Control := ALUContorl.xor
                invalid := false.B
            }
            when(inst.funct3 === "b110".U && inst.funct7 === "b0000000".U){// or
                out.EXU_CTL.ALU_Control := ALUContorl.or
                invalid := false.B
            }
            when(inst.funct3 === "b011".U && inst.funct7 === "b0000000".U){// sltu
                out.EXU_CTL.ALU_Control := ALUContorl.sltu
                invalid := false.B
            }

            when(inst.funct3 === "b001".U && inst.funct7 === "b0000000".U){// sll
                out.EXU_CTL.ALU_Control := ALUContorl.sll
                invalid := false.B
            }
            when(inst.funct3 === "b101".U && inst.funct7 === "b0000000".U){// srl
                out.EXU_CTL.ALU_Control := ALUContorl.srl
                invalid := false.B
            }
            when(inst.funct3 === "b101".U && inst.funct7 === "b0100000".U){// sra
                out.EXU_CTL.ALU_Control := ALUContorl.sra
                invalid := false.B
            }
            when(inst.funct3 === "b010".U && inst.funct7 === "b0000000".U){// slt
                out.EXU_CTL.ALU_Control := ALUContorl.slt
                invalid := false.B
            }
            when(inst.funct3 === "b011".U && inst.funct7 === "b0000000".U){// sltu
                out.EXU_CTL.ALU_Control := ALUContorl.sltu
                invalid := false.B
            }


        }
        is("b0110111".U){// lui
            out.EXU_CTL.ALU_Op := ALUOp.UType
            out.DecodeBundle.imm := inst.immU
            out.EXU_CTL.ALU_Control := ALUContorl.lui
            out.EXU_CTL.ALUWrite2Reg := true.B
            invalid := false.B
        }
        is("b0010111".U){// auipc
            out.EXU_CTL.ALU_Op := ALUOp.UType
            out.DecodeBundle.imm := inst.immU
            out.EXU_CTL.ALU_Src1 := ALUSrc1.pc
            out.EXU_CTL.ALU_Src2 := ALUSrc2.imm
            out.EXU_CTL.ALU_Control := ALUContorl.add
            out.EXU_CTL.ALUWrite2Reg := true.B
            invalid := false.B
        }
        is("b0000011".U){
            out.EXU_CTL.ALU_Op := ALUOp.IType
            out.EXU_CTL.ALU_Src1 := ALUSrc1.rs1
            out.EXU_CTL.ALU_Src2 := ALUSrc2.imm
            out.DecodeBundle.imm := inst.immI
            out.EXU_CTL.ALU_Control := ALUContorl.add
            
            when(inst.funct3 === "b010".U){//lw
                out.EXU_CTL.Mem_Control := MemControl.lw
                invalid := false.B
            }.elsewhen(inst.funct3 === "b100".U){//lbu
                out.EXU_CTL.Mem_Control := MemControl.lbu
                invalid := false.B
            }.elsewhen(inst.funct3 === "b101".U){//lhu
                out.EXU_CTL.Mem_Control := MemControl.lhu
                invalid := false.B                
            }.elsewhen(inst.funct3 === "b000".U){// lb
                out.EXU_CTL.Mem_Control := MemControl.lb
                invalid := false.B    
            }.elsewhen(inst.funct3 === "b001".U){// lh
                out.EXU_CTL.Mem_Control := MemControl.lh
                invalid := false.B
            }
        }
        is("b0100011".U){
            out.EXU_CTL.ALU_Op := ALUOp.SType
            out.EXU_CTL.ALU_Src1 := ALUSrc1.rs1
            out.EXU_CTL.ALU_Src2 := ALUSrc2.imm
            out.DecodeBundle.imm := inst.immS
            out.EXU_CTL.ALU_Control := ALUContorl.add
            when(inst.funct3 === "b010".U){// sw
                out.EXU_CTL.Mem_Control := MemControl.sw
                invalid := false.B
                
            }.elsewhen(inst.funct3 === "b000".U){// sb
                out.EXU_CTL.Mem_Control := MemControl.sb
                invalid := false.B
            }.elsewhen(inst.funct3 === "b001".U){
                out.EXU_CTL.Mem_Control := MemControl.sh
                invalid := false.B
            }
        }
        is("b1101111".U){// jal
            out.EXU_CTL.ALU_Op := ALUOp.JType
            out.EXU_CTL.ALU_Src1 := ALUSrc1.pc
            out.EXU_CTL.ALU_Src2 := ALUSrc2.imm
            out.DecodeBundle.imm := inst.immJ
            out.EXU_CTL.ALU_Control := ALUContorl.add
            out.EXU_CTL.ALU_Jump := Jump.jal
            invalid := false.B
        }

        is("b1100011".U){// beq
            out.EXU_CTL.ALU_Op := ALUOp.BType
            out.EXU_CTL.ALU_Src1 := ALUSrc1.pc
            out.EXU_CTL.ALU_Src2 := ALUSrc2.imm
            out.DecodeBundle.imm := inst.immB
            out.EXU_CTL.ALU_Control := ALUContorl.add
            switch(inst.funct3){
                is("b000".U){
                    out.EXU_CTL.Branch_Control := BranchControl.beq
                    invalid := false.B
                }
                is("b001".U){
                    out.EXU_CTL.Branch_Control := BranchControl.bne
                    invalid := false.B
                }
                is("b100".U){
                    out.EXU_CTL.Branch_Control := BranchControl.blt
                    invalid := false.B                    
                }
                is("b101".U){
                    out.EXU_CTL.Branch_Control := BranchControl.bge
                    invalid := false.B                    
                }
                is("b110".U){
                    out.EXU_CTL.Branch_Control := BranchControl.bltu
                    invalid := false.B                    
                }
                is("b111".U){
                    out.EXU_CTL.Branch_Control := BranchControl.bgeu
                    invalid := false.B                    
                }
            }
        }

        }
    }
}