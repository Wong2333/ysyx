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
    val in = IO(Flipped(Decoupled(new Bundle {
        val pc = Input(UInt(32.W))
        val inst = Input(UInt(32.W))
    })))

    val out = IO(Decoupled(new Bundle {
        val DecodeBundle = new Bundle {
            val rs1 = Output(UInt(5.W))
            val rs2 = Output(UInt(5.W))
            val rd = Output(UInt(5.W))
            val imm = Output(UInt(32.W))
            val pc = Output(UInt(32.W))
        }
        val EXU_CTL = new EXUCTLBundle
    }))
    in.ready := true.B
    out.valid := in.fire
    //printf("%x\n",in.valid)
    val trap = RegInit(false.B)
    val invalid = WireDefault(false.B)

    val ebreak = Module(new EbreakBlackBox())
    ebreak.io.trap := trap
    

    val invalid_instruction = Module(new InvalidInstructionBlackBox())
    invalid_instruction.io.pc := in.bits.pc
    invalid_instruction.io.inst := in.bits.inst
    invalid_instruction.io.invalid := invalid

    val inst = in.bits.inst.asTypeOf(new InstDecode)

    out.bits.DecodeBundle.rs1 := inst.rs1
    out.bits.DecodeBundle.rs2 := inst.rs2
    out.bits.DecodeBundle.rd := inst.rd
    out.bits.DecodeBundle.pc := in.bits.pc
    out.bits.DecodeBundle.imm := DontCare

    out.bits.EXU_CTL.ALU_Op := DontCare
    out.bits.EXU_CTL.ALU_Control := DontCare
    out.bits.EXU_CTL.ALU_Src1 := DontCare
    out.bits.EXU_CTL.ALU_Src2 := DontCare
    out.bits.EXU_CTL.ALU_Jump := Jump.none
    out.bits.EXU_CTL.Mem_Control := MemControl.none
    out.bits.EXU_CTL.Branch_Control := BranchControl.none
    out.bits.EXU_CTL.ALUWrite2Reg := false.B
    out.bits.EXU_CTL.CSR_Contorl := CSRControl.none

    invalid := in.valid
    val cycle = RegInit(0.U(32.W))
    cycle := cycle + 1.U
    when(reset.asBool){
        invalid := false.B
    }.otherwise{
        printf("cycle: %x pc: 0x%x inst: 0x%x out.valid: %x\n",cycle,in.bits.pc,in.bits.inst,out.valid)
        switch(inst.opcode){
        is("b0010011".U){
            out.bits.EXU_CTL.ALU_Op := ALUOp.IType
            out.bits.DecodeBundle.imm := inst.immI
            out.bits.EXU_CTL.ALU_Src1 := ALUSrc1.rs1
            out.bits.EXU_CTL.ALU_Src2 := ALUSrc2.imm
            out.bits.EXU_CTL.ALUWrite2Reg := true.B
            when(inst.funct3 === "b000".U){// addi
                out.bits.EXU_CTL.ALU_Control := ALUContorl.add
                invalid := false.B
            }
            when(inst.funct3 === "b011".U){// sltiu
                out.bits.EXU_CTL.ALU_Control := ALUContorl.sltu
                invalid := false.B
            }
            when(inst.funct3 === "b010".U){// slti
                out.bits.EXU_CTL.ALU_Control := ALUContorl.slt
                invalid := false.B
            }
            when(inst.funct3 === "b001".U && inst.immI(11,5) === "b0".U ){// slli
                out.bits.EXU_CTL.ALU_Control := ALUContorl.sll
                out.bits.EXU_CTL.ALU_Src2 := ALUSrc2.shamt
                invalid := false.B
            }
            when(inst.funct3 === "b101".U && inst.immI(11,5) === "b0".U){//srli
                out.bits.EXU_CTL.ALU_Control := ALUContorl.srl
                out.bits.EXU_CTL.ALU_Src2 := ALUSrc2.shamt
                invalid := false.B   
            }
            when(inst.funct3 === "b101".U && inst.immI(11,5) === "b0100000".U){//srai
                out.bits.EXU_CTL.ALU_Control := ALUContorl.sra
                out.bits.EXU_CTL.ALU_Src2 := ALUSrc2.shamt
                invalid := false.B   
            }

            when(inst.funct3 === "b111".U){// andi
                out.bits.EXU_CTL.ALU_Control := ALUContorl.and
                invalid := false.B
            }
            when(inst.funct3 === "b100".U){// xori
                out.bits.EXU_CTL.ALU_Control := ALUContorl.xor
                invalid := false.B
            }
            when(inst.funct3 === "b110".U){// ori
                out.bits.EXU_CTL.ALU_Control := ALUContorl.or
                invalid := false.B
            }
        }
        is("b1100111".U){
            out.bits.EXU_CTL.ALU_Op := ALUOp.IType
            out.bits.DecodeBundle.imm := inst.immI
            out.bits.EXU_CTL.ALU_Src1 := ALUSrc1.rs1
            out.bits.EXU_CTL.ALU_Src2 := ALUSrc2.imm
            when(inst.funct3 === "b000".U){//jalr
                out.bits.EXU_CTL.ALU_Jump := Jump.jal
                out.bits.EXU_CTL.ALU_Control := ALUContorl.add
                invalid := false.B
            }
        }
        is("b1110011".U){
            when(inst.funct3 === "b000".U && inst.immI === 1.U && 
                        inst.rs1 === 0.U && inst.rd === 0.U){//ebreak
                    invalid := false.B
                    trap := true.B
                
            }.elsewhen(inst.funct3 === "b010".U){// csrrs
                out.bits.DecodeBundle.rd := inst.rd
                out.bits.DecodeBundle.rs1 := inst.rs1
                out.bits.DecodeBundle.imm := inst.immI
                out.bits.EXU_CTL.ALU_Src1 := ALUSrc1.rs1
                out.bits.EXU_CTL.ALU_Src2 := ALUSrc2.csr
                out.bits.EXU_CTL.ALU_Control := ALUContorl.or
                out.bits.EXU_CTL.CSR_Contorl := CSRControl.csrrs
                invalid := false.B
            }.elsewhen(inst.funct3 === "b001".U){// csrws
                out.bits.DecodeBundle.rd := inst.rd
                out.bits.DecodeBundle.rs1 := inst.rs1
                out.bits.DecodeBundle.imm := inst.immI
                out.bits.EXU_CTL.CSR_Contorl := CSRControl.csrrw
                invalid := false.B
            }.elsewhen(inst.funct3 === 0.U && inst.rd === 0.U && 
                    inst.rs1 === 0.U && inst.immI === "b001100000010".U){// mret
                    out.bits.EXU_CTL.CSR_Contorl := CSRControl.mret
                    invalid := false.B
            }.elsewhen(inst.funct3 === "b000".U && inst.immI === 0.U && 
                        inst.rs1 === 0.U && inst.rd === 0.U){ // ecall
                out.bits.EXU_CTL.CSR_Contorl := CSRControl.ecall
                out.bits.DecodeBundle.pc := in.bits.pc
                invalid := false.B
            }
        }
        is("b0110011".U){
            out.bits.EXU_CTL.ALU_Op := ALUOp.RType
            out.bits.EXU_CTL.ALU_Src1 := ALUSrc1.rs1
            out.bits.EXU_CTL.ALU_Src2 := ALUSrc2.rs2
            out.bits.EXU_CTL.ALUWrite2Reg := true.B
            when(inst.funct3 === "b000".U && inst.funct7 === "b0000000".U){// add
                out.bits.EXU_CTL.ALU_Control := ALUContorl.add
                invalid := false.B
            }
            when(inst.funct3 === "b000".U && inst.funct7 === "b0100000".U){// sub
                out.bits.EXU_CTL.ALU_Control := ALUContorl.sub
                invalid := false.B
            }
            when(inst.funct3 === "b111".U && inst.funct7 === "b0000000".U){// and
                out.bits.EXU_CTL.ALU_Control := ALUContorl.and
                invalid := false.B
            }
            when(inst.funct3 === "b100".U && inst.funct7 === "b0000000".U){// xor
                out.bits.EXU_CTL.ALU_Control := ALUContorl.xor
                invalid := false.B
            }
            when(inst.funct3 === "b110".U && inst.funct7 === "b0000000".U){// or
                out.bits.EXU_CTL.ALU_Control := ALUContorl.or
                invalid := false.B
            }
            when(inst.funct3 === "b011".U && inst.funct7 === "b0000000".U){// sltu
                out.bits.EXU_CTL.ALU_Control := ALUContorl.sltu
                invalid := false.B
            }

            when(inst.funct3 === "b001".U && inst.funct7 === "b0000000".U){// sll
                out.bits.EXU_CTL.ALU_Control := ALUContorl.sll
                invalid := false.B
            }
            when(inst.funct3 === "b101".U && inst.funct7 === "b0000000".U){// srl
                out.bits.EXU_CTL.ALU_Control := ALUContorl.srl
                invalid := false.B
            }
            when(inst.funct3 === "b101".U && inst.funct7 === "b0100000".U){// sra
                out.bits.EXU_CTL.ALU_Control := ALUContorl.sra
                invalid := false.B
            }
            when(inst.funct3 === "b010".U && inst.funct7 === "b0000000".U){// slt
                out.bits.EXU_CTL.ALU_Control := ALUContorl.slt
                invalid := false.B
            }
            when(inst.funct3 === "b011".U && inst.funct7 === "b0000000".U){// sltu
                out.bits.EXU_CTL.ALU_Control := ALUContorl.sltu
                invalid := false.B
            }


        }
        is("b0110111".U){// lui
            out.bits.EXU_CTL.ALU_Op := ALUOp.UType
            out.bits.DecodeBundle.imm := inst.immU
            out.bits.EXU_CTL.ALU_Control := ALUContorl.lui
            out.bits.EXU_CTL.ALUWrite2Reg := true.B
            invalid := false.B
        }
        is("b0010111".U){// auipc
            out.bits.EXU_CTL.ALU_Op := ALUOp.UType
            out.bits.DecodeBundle.imm := inst.immU
            out.bits.EXU_CTL.ALU_Src1 := ALUSrc1.pc
            out.bits.EXU_CTL.ALU_Src2 := ALUSrc2.imm
            out.bits.EXU_CTL.ALU_Control := ALUContorl.add
            out.bits.EXU_CTL.ALUWrite2Reg := true.B
            invalid := false.B
        }
        is("b0000011".U){
            out.bits.EXU_CTL.ALU_Op := ALUOp.IType
            out.bits.EXU_CTL.ALU_Src1 := ALUSrc1.rs1
            out.bits.EXU_CTL.ALU_Src2 := ALUSrc2.imm
            out.bits.DecodeBundle.imm := inst.immI
            out.bits.EXU_CTL.ALU_Control := ALUContorl.add
            
            when(inst.funct3 === "b010".U){//lw
                out.bits.EXU_CTL.Mem_Control := MemControl.lw
                invalid := false.B
            }.elsewhen(inst.funct3 === "b100".U){//lbu
                out.bits.EXU_CTL.Mem_Control := MemControl.lbu
                invalid := false.B
            }.elsewhen(inst.funct3 === "b101".U){//lhu
                out.bits.EXU_CTL.Mem_Control := MemControl.lhu
                invalid := false.B                
            }.elsewhen(inst.funct3 === "b000".U){// lb
                out.bits.EXU_CTL.Mem_Control := MemControl.lb
                invalid := false.B    
            }.elsewhen(inst.funct3 === "b001".U){// lh
                out.bits.EXU_CTL.Mem_Control := MemControl.lh
                invalid := false.B
            }
        }
        is("b0100011".U){
            out.bits.EXU_CTL.ALU_Op := ALUOp.SType
            out.bits.EXU_CTL.ALU_Src1 := ALUSrc1.rs1
            out.bits.EXU_CTL.ALU_Src2 := ALUSrc2.imm
            out.bits.DecodeBundle.imm := inst.immS
            out.bits.EXU_CTL.ALU_Control := ALUContorl.add
            when(inst.funct3 === "b010".U){// sw
                out.bits.EXU_CTL.Mem_Control := MemControl.sw
                invalid := false.B
                
            }.elsewhen(inst.funct3 === "b000".U){// sb
                out.bits.EXU_CTL.Mem_Control := MemControl.sb
                invalid := false.B
            }.elsewhen(inst.funct3 === "b001".U){
                out.bits.EXU_CTL.Mem_Control := MemControl.sh
                invalid := false.B
            }
        }
        is("b1101111".U){// jal
            out.bits.EXU_CTL.ALU_Op := ALUOp.JType
            out.bits.EXU_CTL.ALU_Src1 := ALUSrc1.pc
            out.bits.EXU_CTL.ALU_Src2 := ALUSrc2.imm
            out.bits.DecodeBundle.imm := inst.immJ
            out.bits.EXU_CTL.ALU_Control := ALUContorl.add
            out.bits.EXU_CTL.ALU_Jump := Jump.jal
            invalid := false.B
        }

        is("b1100011".U){// beq
            out.bits.EXU_CTL.ALU_Op := ALUOp.BType
            out.bits.EXU_CTL.ALU_Src1 := ALUSrc1.pc
            out.bits.EXU_CTL.ALU_Src2 := ALUSrc2.imm
            out.bits.DecodeBundle.imm := inst.immB
            out.bits.EXU_CTL.ALU_Control := ALUContorl.add
            switch(inst.funct3){
                is("b000".U){
                    out.bits.EXU_CTL.Branch_Control := BranchControl.beq
                    invalid := false.B
                }
                is("b001".U){
                    out.bits.EXU_CTL.Branch_Control := BranchControl.bne
                    invalid := false.B
                }
                is("b100".U){
                    out.bits.EXU_CTL.Branch_Control := BranchControl.blt
                    invalid := false.B                    
                }
                is("b101".U){
                    out.bits.EXU_CTL.Branch_Control := BranchControl.bge
                    invalid := false.B                    
                }
                is("b110".U){
                    out.bits.EXU_CTL.Branch_Control := BranchControl.bltu
                    invalid := false.B                    
                }
                is("b111".U){
                    out.bits.EXU_CTL.Branch_Control := BranchControl.bgeu
                    invalid := false.B                    
                }
            }
        }

        }
    }
}