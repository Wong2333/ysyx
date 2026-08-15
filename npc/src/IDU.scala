package npc

import chisel3._
import chisel3.util._
import npc.EXUs._
import npc.PMU._

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
        val ebreak_trap = Input(Bool())
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

class DecodeBundle extends Bundle{
        val inst = Output(UInt(32.W))
        val invalid = Output(Bool())
        val rs1 = Output(UInt(5.W))
        val rs2 = Output(UInt(5.W))
        val rd = Output(UInt(5.W))
        val imm = Output(UInt(32.W))
        val pc = Output(UInt(32.W))
    

}

class IDUBundle extends Bundle{
    val decode = new DecodeBundle
    val exu_ctl = new EXUCTLBundle
    val rs1_data = UInt(32.W)
    val rs2_data = UInt(32.W)
    val csr_rdata = UInt(32.W)
}

// 各流水线阶段的"写寄存器信息"，供 IDU 做 RAW 冒险检测（讲义方案）
class StageWriteInfoBundle extends Bundle{
    val valid = Output(Bool())    // 本拍该阶段是否有指令处于写回窗口
    val wen   = Output(Bool())    // 该指令是否真的写寄存器（store/branch 等不写）
    val rd    = Output(UInt(5.W)) // 写入的寄存器编号
}

class ysyx_26030092_IDU extends Module{
    val in = IO(Flipped(Decoupled(new Bundle {
        val pc = Input(UInt(32.W))
        val inst = Input(UInt(32.W))
    })))
    val flush = IO(Input(Bool()))
    val out = IO(Decoupled(new IDUBundle))

    // 各阶段写寄存器信息（RAW 冒险检测用）
    val ex_wb_info  = IO(Flipped(new StageWriteInfoBundle))
    val mem_wb_info = IO(Flipped(new StageWriteInfoBundle))
    val wb_wb_info  = IO(Flipped(new StageWriteInfoBundle))

    // 寄存器堆读端口（译码阶段组合读取，读数据随流水线传入 EXU）
    val RFS_ReadPort = IO(new Bundle {
        val RegReadPort = new ReadRegPortBundle
        val CSRReadPort = new CSRReadPortBundle
    })

    val inst = in.bits.inst.asTypeOf(new InstDecode)

    // ========== RAW 冒险检测（讲义方案） ==========
    // 与任一阶段"正在写回"的寄存器冲突（rs=rd 且 rs≠x0）→ 等待（stall）
    // 等待实现：in.ready / out.valid 置无效，握手反压自然传到 IFU
    def conflict(rs: UInt, info: StageWriteInfoBundle): Bool =
        info.valid && info.wen && rs =/= 0.U && rs === info.rd

    val stall =
        conflict(inst.rs1, ex_wb_info)  || conflict(inst.rs2, ex_wb_info)  ||
        conflict(inst.rs1, mem_wb_info) || conflict(inst.rs2, mem_wb_info) ||
        conflict(inst.rs1, wb_wb_info)  || conflict(inst.rs2, wb_wb_info)

    in.ready := out.ready & ~flush & ~stall
    out.valid := in.valid & ~flush & ~stall
    out.bits.decode.inst := in.bits.inst

    PerfCounter("IDU_pipe_idle") := !in.valid

    val trap = RegInit(false.B)
    val invalid = WireDefault(true.B)
    out.bits.decode.invalid := invalid

    out.bits.decode.rs1 := inst.rs1
    out.bits.decode.rs2 := inst.rs2
    out.bits.decode.rd := inst.rd
    out.bits.decode.pc := in.bits.pc
    out.bits.decode.imm := DontCare

    out.bits.exu_ctl.ALU_Op := DontCare
    out.bits.exu_ctl.ALU_Control := DontCare
    out.bits.exu_ctl.ALU_Src1 := DontCare
    out.bits.exu_ctl.ALU_Src2 := DontCare
    out.bits.exu_ctl.ALU_Jump := Jump.none
    out.bits.exu_ctl.Mem_Control := MemControl.none
    out.bits.exu_ctl.Branch_Control := BranchControl.none
    out.bits.exu_ctl.ALUWrite2Reg := false.B
    out.bits.exu_ctl.CSR_Contorl := CSRControl.none
    out.bits.exu_ctl.fence_i := false.B


    switch(inst.opcode){
        is("b0010011".U){
            out.bits.exu_ctl.ALU_Op := ALUOp.IType
            out.bits.decode.imm := inst.immI
            out.bits.exu_ctl.ALU_Src1 := ALUSrc1.rs1
            out.bits.exu_ctl.ALU_Src2 := ALUSrc2.imm
            out.bits.exu_ctl.ALUWrite2Reg := true.B
            when(inst.funct3 === "b000".U){// addi
                out.bits.exu_ctl.ALU_Control := ALUContorl.add
                invalid := false.B
            }
            when(inst.funct3 === "b011".U){// sltiu
                out.bits.exu_ctl.ALU_Control := ALUContorl.sltu
                invalid := false.B
            }
            when(inst.funct3 === "b010".U){// slti
                out.bits.exu_ctl.ALU_Control := ALUContorl.slt
                invalid := false.B
            }
            when(inst.funct3 === "b001".U && inst.immI(11,5) === "b0".U ){// slli
                out.bits.exu_ctl.ALU_Control := ALUContorl.sll
                out.bits.exu_ctl.ALU_Src2 := ALUSrc2.shamt
                invalid := false.B
            }
            when(inst.funct3 === "b101".U && inst.immI(11,5) === "b0".U){//srli
                out.bits.exu_ctl.ALU_Control := ALUContorl.srl
                out.bits.exu_ctl.ALU_Src2 := ALUSrc2.shamt
                invalid := false.B   
            }
            when(inst.funct3 === "b101".U && inst.immI(11,5) === "b0100000".U){//srai
                out.bits.exu_ctl.ALU_Control := ALUContorl.sra
                out.bits.exu_ctl.ALU_Src2 := ALUSrc2.shamt
                invalid := false.B   
            }

            when(inst.funct3 === "b111".U){// andi
                out.bits.exu_ctl.ALU_Control := ALUContorl.and
                invalid := false.B
            }
            when(inst.funct3 === "b100".U){// xori
                out.bits.exu_ctl.ALU_Control := ALUContorl.xor
                invalid := false.B
            }
            when(inst.funct3 === "b110".U){// ori
                out.bits.exu_ctl.ALU_Control := ALUContorl.or
                invalid := false.B
            }
        }
        is("b1100111".U){
            out.bits.exu_ctl.ALU_Op := ALUOp.IType
            out.bits.decode.imm := inst.immI
            out.bits.exu_ctl.ALU_Src1 := ALUSrc1.rs1
            out.bits.exu_ctl.ALU_Src2 := ALUSrc2.imm
            when(inst.funct3 === "b000".U){//jalr
                out.bits.exu_ctl.ALU_Jump := Jump.jal
                out.bits.exu_ctl.ALU_Control := ALUContorl.add
                invalid := false.B
            }
        }
        is("b1110011".U){
            when(inst.funct3 === "b000".U && inst.immI === 1.U && 
                        inst.rs1 === 0.U && inst.rd === 0.U){//ebreak
                    invalid := false.B
                    out.bits.exu_ctl.CSR_Contorl := CSRControl.ebreak
                
            }.elsewhen(inst.funct3 === "b010".U){// csrrs
                out.bits.decode.rd := inst.rd
                out.bits.decode.rs1 := inst.rs1
                out.bits.decode.imm := inst.immI
                out.bits.exu_ctl.ALU_Src1 := ALUSrc1.rs1
                out.bits.exu_ctl.ALU_Src2 := ALUSrc2.csr
                out.bits.exu_ctl.ALU_Control := ALUContorl.or
                out.bits.exu_ctl.CSR_Contorl := CSRControl.csrrs
                invalid := false.B
            }.elsewhen(inst.funct3 === "b001".U){// csrws
                out.bits.decode.rd := inst.rd
                out.bits.decode.rs1 := inst.rs1
                out.bits.decode.imm := inst.immI
                out.bits.exu_ctl.CSR_Contorl := CSRControl.csrrw
                invalid := false.B
            }.elsewhen(inst.funct3 === 0.U && inst.rd === 0.U && 
                    inst.rs1 === 0.U && inst.immI === "b001100000010".U){// mret
                    out.bits.exu_ctl.CSR_Contorl := CSRControl.mret
                    invalid := false.B
            }.elsewhen(inst.funct3 === "b000".U && inst.immI === 0.U && 
                        inst.rs1 === 0.U && inst.rd === 0.U){ // ecall
                out.bits.exu_ctl.CSR_Contorl := CSRControl.ecall
                out.bits.decode.pc := in.bits.pc
                invalid := false.B
            }
        }
        is("b0110011".U){
            out.bits.exu_ctl.ALU_Op := ALUOp.RType
            out.bits.exu_ctl.ALU_Src1 := ALUSrc1.rs1
            out.bits.exu_ctl.ALU_Src2 := ALUSrc2.rs2
            out.bits.exu_ctl.ALUWrite2Reg := true.B
            when(inst.funct3 === "b000".U && inst.funct7 === "b0000000".U){// add
                out.bits.exu_ctl.ALU_Control := ALUContorl.add
                invalid := false.B
            }
            when(inst.funct3 === "b000".U && inst.funct7 === "b0100000".U){// sub
                out.bits.exu_ctl.ALU_Control := ALUContorl.sub
                invalid := false.B
            }
            when(inst.funct3 === "b111".U && inst.funct7 === "b0000000".U){// and
                out.bits.exu_ctl.ALU_Control := ALUContorl.and
                invalid := false.B
            }
            when(inst.funct3 === "b100".U && inst.funct7 === "b0000000".U){// xor
                out.bits.exu_ctl.ALU_Control := ALUContorl.xor
                invalid := false.B
            }
            when(inst.funct3 === "b110".U && inst.funct7 === "b0000000".U){// or
                out.bits.exu_ctl.ALU_Control := ALUContorl.or
                invalid := false.B
            }
            when(inst.funct3 === "b011".U && inst.funct7 === "b0000000".U){// sltu
                out.bits.exu_ctl.ALU_Control := ALUContorl.sltu
                invalid := false.B
            }

            when(inst.funct3 === "b001".U && inst.funct7 === "b0000000".U){// sll
                out.bits.exu_ctl.ALU_Control := ALUContorl.sll
                invalid := false.B
            }
            when(inst.funct3 === "b101".U && inst.funct7 === "b0000000".U){// srl
                out.bits.exu_ctl.ALU_Control := ALUContorl.srl
                invalid := false.B
            }
            when(inst.funct3 === "b101".U && inst.funct7 === "b0100000".U){// sra
                out.bits.exu_ctl.ALU_Control := ALUContorl.sra
                invalid := false.B
            }
            when(inst.funct3 === "b010".U && inst.funct7 === "b0000000".U){// slt
                out.bits.exu_ctl.ALU_Control := ALUContorl.slt
                invalid := false.B
            }
            when(inst.funct3 === "b011".U && inst.funct7 === "b0000000".U){// sltu
                out.bits.exu_ctl.ALU_Control := ALUContorl.sltu
                invalid := false.B
            }


        }
        is("b0110111".U){// lui
            out.bits.exu_ctl.ALU_Op := ALUOp.UType
            out.bits.decode.imm := inst.immU
            out.bits.exu_ctl.ALU_Control := ALUContorl.lui
            out.bits.exu_ctl.ALUWrite2Reg := true.B
            invalid := false.B
        }
        is("b0010111".U){// auipc
            out.bits.exu_ctl.ALU_Op := ALUOp.UType
            out.bits.decode.imm := inst.immU
            out.bits.exu_ctl.ALU_Src1 := ALUSrc1.pc
            out.bits.exu_ctl.ALU_Src2 := ALUSrc2.imm
            out.bits.exu_ctl.ALU_Control := ALUContorl.add
            out.bits.exu_ctl.ALUWrite2Reg := true.B
            invalid := false.B
        }
        is("b0000011".U){
            out.bits.exu_ctl.ALU_Op := ALUOp.IType
            out.bits.exu_ctl.ALU_Src1 := ALUSrc1.rs1
            out.bits.exu_ctl.ALU_Src2 := ALUSrc2.imm
            out.bits.decode.imm := inst.immI
            out.bits.exu_ctl.ALU_Control := ALUContorl.add
            when(inst.funct3 === "b010".U){//lw
                out.bits.exu_ctl.Mem_Control := MemControl.lw
                invalid := false.B
            }.elsewhen(inst.funct3 === "b100".U){//lbu
                out.bits.exu_ctl.Mem_Control := MemControl.lbu
                invalid := false.B
            }.elsewhen(inst.funct3 === "b101".U){//lhu
                out.bits.exu_ctl.Mem_Control := MemControl.lhu
                invalid := false.B                
            }.elsewhen(inst.funct3 === "b000".U){// lb
                out.bits.exu_ctl.Mem_Control := MemControl.lb
                invalid := false.B    
            }.elsewhen(inst.funct3 === "b001".U){// lh
                out.bits.exu_ctl.Mem_Control := MemControl.lh
                invalid := false.B
            }
        }
        is("b0100011".U){
            out.bits.exu_ctl.ALU_Op := ALUOp.SType
            out.bits.exu_ctl.ALU_Src1 := ALUSrc1.rs1
            out.bits.exu_ctl.ALU_Src2 := ALUSrc2.imm
            out.bits.decode.imm := inst.immS
            out.bits.exu_ctl.ALU_Control := ALUContorl.add
            when(inst.funct3 === "b010".U){// sw
                out.bits.exu_ctl.Mem_Control := MemControl.sw
                invalid := false.B
                
            }.elsewhen(inst.funct3 === "b000".U){// sb
                out.bits.exu_ctl.Mem_Control := MemControl.sb
                invalid := false.B
            }.elsewhen(inst.funct3 === "b001".U){
                out.bits.exu_ctl.Mem_Control := MemControl.sh
                invalid := false.B
            }
        }
        is("b1101111".U){// jal
            out.bits.exu_ctl.ALU_Op := ALUOp.JType
            out.bits.exu_ctl.ALU_Src1 := ALUSrc1.pc
            out.bits.exu_ctl.ALU_Src2 := ALUSrc2.imm
            out.bits.decode.imm := inst.immJ
            out.bits.exu_ctl.ALU_Control := ALUContorl.add
            out.bits.exu_ctl.ALU_Jump := Jump.jal
            invalid := false.B
        }

        is("b1100011".U){// beq
            out.bits.exu_ctl.ALU_Op := ALUOp.BType
            out.bits.exu_ctl.ALU_Src1 := ALUSrc1.pc
            out.bits.exu_ctl.ALU_Src2 := ALUSrc2.imm
            out.bits.decode.imm := inst.immB
            out.bits.exu_ctl.ALU_Control := ALUContorl.add
            switch(inst.funct3){
                is("b000".U){
                    out.bits.exu_ctl.Branch_Control := BranchControl.beq
                    invalid := false.B
                }
                is("b001".U){
                    out.bits.exu_ctl.Branch_Control := BranchControl.bne
                    invalid := false.B
                }
                is("b100".U){
                    out.bits.exu_ctl.Branch_Control := BranchControl.blt
                    invalid := false.B                    
                }
                is("b101".U){
                    out.bits.exu_ctl.Branch_Control := BranchControl.bge
                    invalid := false.B                    
                }
                is("b110".U){
                    out.bits.exu_ctl.Branch_Control := BranchControl.bltu
                    invalid := false.B                    
                }
                is("b111".U){
                    out.bits.exu_ctl.Branch_Control := BranchControl.bgeu
                    invalid := false.B
                }
            }
        }
        is("b0001111".U){ //fence.i
            when(inst.funct3 === "b001".U){
                out.bits.exu_ctl.fence_i := true.B
                invalid := false.B
            }
        }
    }

    // 译码阶段读取寄存器堆：rs1/rs2 无条件组合读取（x0 恒为 0，未用数据无副作用）
    RFS_ReadPort.RegReadPort.raddr1 := inst.rs1
    RFS_ReadPort.RegReadPort.raddr2 := inst.rs2
    out.bits.rs1_data := RFS_ReadPort.RegReadPort.rdata1
    out.bits.rs2_data := RFS_ReadPort.RegReadPort.rdata2

    // CSR 读地址：mret 读 mepc；csrrs/csrrw 读 imm(11,0)；其余指令不读 CSR
    RFS_ReadPort.CSRReadPort.CSRRAddr := 0.U
    switch(out.bits.exu_ctl.CSR_Contorl){
        is(CSRControl.mret){
            RFS_ReadPort.CSRReadPort.CSRRAddr := "h341".U
        }
        is(CSRControl.csrrs, CSRControl.csrrw){
            RFS_ReadPort.CSRReadPort.CSRRAddr := out.bits.decode.imm(11,0)
        }
    }
    out.bits.csr_rdata := RFS_ReadPort.CSRReadPort.CSRRData

    // 指令分类计数（原 switch 分支内 out.fire 触发，统一在此按 opcode 判断）
    PerfCounter("IDU_calc") := (inst.opcode === "b0010011".U || inst.opcode === "b0110011".U) && out.fire
    PerfCounter("IDU_jump") := (inst.opcode === "b1100111".U || inst.opcode === "b1101111".U) && out.fire
    PerfCounter("IDU_csr") := (inst.opcode === "b1110011".U) && out.fire
    PerfCounter("IDU_load") := (inst.opcode === "b0000011".U) && out.fire
    PerfCounter("IDU_store") := (inst.opcode === "b0100011".U) && out.fire
    PerfCounter("IDU_branch") := (inst.opcode === "b1100011".U) && out.fire
}