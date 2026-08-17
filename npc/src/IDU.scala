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

class StageWriteInfoBundle extends Bundle{
    val wen   = Output(Bool())    // 本拍该阶段正在写寄存器（含阶段有效性）
    val rd    = Output(UInt(5.W)) 
}

class CSRWriteInfoBundle extends Bundle{
    val wen   = Output(Bool())    // 本拍该阶段正在写 CSR（含阶段有效性）
    val addr  = Output(UInt(12.W))
}

class ysyx_26030092_IDU extends Module{
    val in = IO(Flipped(Decoupled(new Bundle {
        val pc = Input(UInt(32.W))
        val inst = Input(UInt(32.W))
    })))
    val flush = IO(Input(Bool()))
    val out = IO(Decoupled(new IDUBundle))
    val out_comb = Wire(new IDUBundle)

    val ex_wb_info  = IO(Flipped(new StageWriteInfoBundle))
    val mem_wb_info = IO(Flipped(new StageWriteInfoBundle))
    val wb_wb_info  = IO(Flipped(new StageWriteInfoBundle))
    val ex_csr_info = IO(Flipped(new CSRWriteInfoBundle))
    val mem_csr_info = IO(Flipped(new CSRWriteInfoBundle))
    val wb_csr_info = IO(Flipped(new CSRWriteInfoBundle))

    // 寄存器堆读端口（译码阶段组合读取，读数据随流水线传入 EXU）
    val RFS_ReadPort = IO(new Bundle {
        val RegReadPort = new ReadRegPortBundle
        val CSRReadPort = new CSRReadPortBundle
    })

    val inst = in.bits.inst.asTypeOf(new InstDecode)

    // RAW 冒险
    def conflict(rs: UInt, info: StageWriteInfoBundle): Bool =
        info.wen && rs =/= 0.U && rs === info.rd

    // CSR 写后读：csrrs/csrrw 读 imm(11,0)，mret 读 mepc(0x341)
    val csr_read_addr = MuxCase(0.U(12.W), Seq(
        (out_comb.exu_ctl.sys_op === SysOp.mret)          -> "h341".U,
        (out_comb.exu_ctl.csr_op =/= CSROp.none)          -> out_comb.decode.imm(11,0)
    ))
    val csr_read = out_comb.exu_ctl.sys_op === SysOp.mret || out_comb.exu_ctl.csr_op =/= CSROp.none

    val stall =
        conflict(inst.rs1, ex_wb_info)  || conflict(inst.rs2, ex_wb_info)  ||
        conflict(inst.rs1, mem_wb_info) || conflict(inst.rs2, mem_wb_info) ||
        conflict(inst.rs1, wb_wb_info)  || conflict(inst.rs2, wb_wb_info) ||
        (csr_read && ((ex_csr_info.wen && csr_read_addr === ex_csr_info.addr) ||
                      (mem_csr_info.wen && csr_read_addr === mem_csr_info.addr) ||
                      (wb_csr_info.wen && csr_read_addr === wb_csr_info.addr)))

    val out_valid = RegInit(false.B)
    in.ready := ~out_valid & ~flush & ~stall
    out.valid := out_valid & ~flush
    out_comb.decode.inst := in.bits.inst

    val out_reg = Reg(new IDUBundle)
    out.bits := out_reg
    when(in.fire){
        out_reg := out_comb
        out_valid := true.B
    }
    when(out.fire | flush){
        out_valid := false.B
    }

    PerfCounter("IDU_pipe_idle") := !in.valid

    val invalid = WireDefault(true.B)

    out_comb.decode.invalid := invalid

    out_comb.decode.rd := inst.rd
    out_comb.decode.pc := in.bits.pc
    out_comb.decode.imm := DontCare

    out_comb.exu_ctl.ALU_Src1    := ALUSrc1.rs1
    out_comb.exu_ctl.ALU_Src2    := ALUSrc2.imm
    out_comb.exu_ctl.ALU_Control := ALUOp.add
    out_comb.exu_ctl.reg_write   := false.B
    out_comb.exu_ctl.wb_src      := WBSrc.none
    out_comb.exu_ctl.mem_op      := MemOp.none
    out_comb.exu_ctl.mem_size    := MemSize.word
    out_comb.exu_ctl.mem_signed  := false.B
    out_comb.exu_ctl.branch_op   := BranchOp.none
    out_comb.exu_ctl.jump_op     := JumpOp.none
    out_comb.exu_ctl.csr_op      := CSROp.none
    out_comb.exu_ctl.sys_op      := SysOp.none
    out_comb.exu_ctl.fence_i     := false.B


    switch(inst.opcode){
        is("b0010011".U){
            out_comb.decode.imm := inst.immI
            out_comb.exu_ctl.ALU_Src1 := ALUSrc1.rs1
            out_comb.exu_ctl.ALU_Src2 := ALUSrc2.imm
            out_comb.exu_ctl.reg_write := true.B
            out_comb.exu_ctl.wb_src := WBSrc.alu
            when(inst.funct3 === "b000".U){// addi
                out_comb.exu_ctl.ALU_Control := ALUOp.add
                invalid := false.B
            }
            when(inst.funct3 === "b011".U){// sltiu
                out_comb.exu_ctl.ALU_Control := ALUOp.sltu
                invalid := false.B
            }
            when(inst.funct3 === "b010".U){// slti
                out_comb.exu_ctl.ALU_Control := ALUOp.slt
                invalid := false.B
            }
            when(inst.funct3 === "b001".U && inst.immI(11,5) === "b0".U ){// slli
                out_comb.exu_ctl.ALU_Control := ALUOp.sll
                out_comb.exu_ctl.ALU_Src2 := ALUSrc2.shamt
                invalid := false.B
            }
            when(inst.funct3 === "b101".U && inst.immI(11,5) === "b0".U){//srli
                out_comb.exu_ctl.ALU_Control := ALUOp.srl
                out_comb.exu_ctl.ALU_Src2 := ALUSrc2.shamt
                invalid := false.B   
            }
            when(inst.funct3 === "b101".U && inst.immI(11,5) === "b0100000".U){//srai
                out_comb.exu_ctl.ALU_Control := ALUOp.sra
                out_comb.exu_ctl.ALU_Src2 := ALUSrc2.shamt
                invalid := false.B   
            }

            when(inst.funct3 === "b111".U){// andi
                out_comb.exu_ctl.ALU_Control := ALUOp.and
                invalid := false.B
            }
            when(inst.funct3 === "b100".U){// xori
                out_comb.exu_ctl.ALU_Control := ALUOp.xor
                invalid := false.B
            }
            when(inst.funct3 === "b110".U){// ori
                out_comb.exu_ctl.ALU_Control := ALUOp.or
                invalid := false.B
            }
        }
        is("b1100111".U){
            out_comb.decode.imm := inst.immI
            out_comb.exu_ctl.ALU_Src1 := ALUSrc1.rs1
            out_comb.exu_ctl.ALU_Src2 := ALUSrc2.imm
            when(inst.funct3 === "b000".U){//jalr
                out_comb.exu_ctl.jump_op := JumpOp.jump
                out_comb.exu_ctl.ALU_Control := ALUOp.add
                out_comb.exu_ctl.reg_write := true.B
                out_comb.exu_ctl.wb_src := WBSrc.pc_plus4
                invalid := false.B
            }
        }
        is("b1110011".U){
            when(inst.funct3 === "b000".U && inst.immI === 1.U && 
                        inst.rs1 === 0.U && inst.rd === 0.U){//ebreak
                    invalid := false.B
                    out_comb.exu_ctl.sys_op := SysOp.ebreak
                
            }.elsewhen(inst.funct3 === "b010".U){// csrrs
                out_comb.decode.rd := inst.rd
                out_comb.decode.imm := inst.immI
                out_comb.exu_ctl.ALU_Src1 := ALUSrc1.rs1
                out_comb.exu_ctl.ALU_Src2 := ALUSrc2.csr
                out_comb.exu_ctl.ALU_Control := ALUOp.or
                out_comb.exu_ctl.csr_op := CSROp.csrrs
                out_comb.exu_ctl.reg_write := true.B
                out_comb.exu_ctl.wb_src := WBSrc.csr
                invalid := false.B
            }.elsewhen(inst.funct3 === "b001".U){// csrws
                out_comb.decode.rd := inst.rd
                out_comb.decode.imm := inst.immI
                out_comb.exu_ctl.csr_op := CSROp.csrrw
                out_comb.exu_ctl.reg_write := true.B
                out_comb.exu_ctl.wb_src := WBSrc.csr
                invalid := false.B
            }.elsewhen(inst.funct3 === 0.U && inst.rd === 0.U && 
                    inst.rs1 === 0.U && inst.immI === "b001100000010".U){// mret
                    out_comb.exu_ctl.sys_op := SysOp.mret
                    invalid := false.B
            }.elsewhen(inst.funct3 === "b000".U && inst.immI === 0.U && 
                        inst.rs1 === 0.U && inst.rd === 0.U){ // ecall
                out_comb.exu_ctl.sys_op := SysOp.ecall
                out_comb.decode.pc := in.bits.pc
                invalid := false.B
            }
        }
        is("b0110011".U){
            out_comb.exu_ctl.ALU_Src1 := ALUSrc1.rs1
            out_comb.exu_ctl.ALU_Src2 := ALUSrc2.rs2
            out_comb.exu_ctl.reg_write := true.B
            out_comb.exu_ctl.wb_src := WBSrc.alu
            when(inst.funct3 === "b000".U && inst.funct7 === "b0000000".U){// add
                out_comb.exu_ctl.ALU_Control := ALUOp.add
                invalid := false.B
            }
            when(inst.funct3 === "b000".U && inst.funct7 === "b0100000".U){// sub
                out_comb.exu_ctl.ALU_Control := ALUOp.sub
                invalid := false.B
            }
            when(inst.funct3 === "b111".U && inst.funct7 === "b0000000".U){// and
                out_comb.exu_ctl.ALU_Control := ALUOp.and
                invalid := false.B
            }
            when(inst.funct3 === "b100".U && inst.funct7 === "b0000000".U){// xor
                out_comb.exu_ctl.ALU_Control := ALUOp.xor
                invalid := false.B
            }
            when(inst.funct3 === "b110".U && inst.funct7 === "b0000000".U){// or
                out_comb.exu_ctl.ALU_Control := ALUOp.or
                invalid := false.B
            }
            when(inst.funct3 === "b011".U && inst.funct7 === "b0000000".U){// sltu
                out_comb.exu_ctl.ALU_Control := ALUOp.sltu
                invalid := false.B
            }

            when(inst.funct3 === "b001".U && inst.funct7 === "b0000000".U){// sll
                out_comb.exu_ctl.ALU_Control := ALUOp.sll
                invalid := false.B
            }
            when(inst.funct3 === "b101".U && inst.funct7 === "b0000000".U){// srl
                out_comb.exu_ctl.ALU_Control := ALUOp.srl
                invalid := false.B
            }
            when(inst.funct3 === "b101".U && inst.funct7 === "b0100000".U){// sra
                out_comb.exu_ctl.ALU_Control := ALUOp.sra
                invalid := false.B
            }
            when(inst.funct3 === "b010".U && inst.funct7 === "b0000000".U){// slt
                out_comb.exu_ctl.ALU_Control := ALUOp.slt
                invalid := false.B
            }
            when(inst.funct3 === "b011".U && inst.funct7 === "b0000000".U){// sltu
                out_comb.exu_ctl.ALU_Control := ALUOp.sltu
                invalid := false.B
            }


        }
        is("b0110111".U){// lui
            out_comb.decode.imm := inst.immU
            out_comb.exu_ctl.ALU_Src2 := ALUSrc2.imm
            out_comb.exu_ctl.ALU_Control := ALUOp.lui
            out_comb.exu_ctl.reg_write := true.B
            out_comb.exu_ctl.wb_src := WBSrc.alu
            invalid := false.B
        }
        is("b0010111".U){// auipc
            out_comb.decode.imm := inst.immU
            out_comb.exu_ctl.ALU_Src1 := ALUSrc1.pc
            out_comb.exu_ctl.ALU_Src2 := ALUSrc2.imm
            out_comb.exu_ctl.ALU_Control := ALUOp.add
            out_comb.exu_ctl.reg_write := true.B
            out_comb.exu_ctl.wb_src := WBSrc.alu
            invalid := false.B
        }
        is("b0000011".U){
            out_comb.exu_ctl.ALU_Src1 := ALUSrc1.rs1
            out_comb.exu_ctl.ALU_Src2 := ALUSrc2.imm
            out_comb.decode.imm := inst.immI
            out_comb.exu_ctl.ALU_Control := ALUOp.add
            out_comb.exu_ctl.mem_op := MemOp.load
            out_comb.exu_ctl.reg_write := true.B
            out_comb.exu_ctl.wb_src := WBSrc.mem
            when(inst.funct3 === "b010".U){//lw
                out_comb.exu_ctl.mem_size := MemSize.word
                invalid := false.B
            }.elsewhen(inst.funct3 === "b100".U){//lbu
                out_comb.exu_ctl.mem_size := MemSize.byte
                invalid := false.B
            }.elsewhen(inst.funct3 === "b101".U){//lhu
                out_comb.exu_ctl.mem_size := MemSize.half
                invalid := false.B                
            }.elsewhen(inst.funct3 === "b000".U){// lb
                out_comb.exu_ctl.mem_size := MemSize.byte
                out_comb.exu_ctl.mem_signed := true.B
                invalid := false.B    
            }.elsewhen(inst.funct3 === "b001".U){// lh
                out_comb.exu_ctl.mem_size := MemSize.half
                out_comb.exu_ctl.mem_signed := true.B
                invalid := false.B
            }
        }
        is("b0100011".U){
            out_comb.exu_ctl.ALU_Src1 := ALUSrc1.rs1
            out_comb.exu_ctl.ALU_Src2 := ALUSrc2.imm
            out_comb.decode.imm := inst.immS
            out_comb.exu_ctl.ALU_Control := ALUOp.add
            out_comb.exu_ctl.mem_op := MemOp.store
            when(inst.funct3 === "b010".U){// sw
                out_comb.exu_ctl.mem_size := MemSize.word
                invalid := false.B
                
            }.elsewhen(inst.funct3 === "b000".U){// sb
                out_comb.exu_ctl.mem_size := MemSize.byte
                invalid := false.B
            }.elsewhen(inst.funct3 === "b001".U){
                out_comb.exu_ctl.mem_size := MemSize.half
                invalid := false.B
            }
        }
        is("b1101111".U){// jal
            out_comb.exu_ctl.ALU_Src1 := ALUSrc1.pc
            out_comb.exu_ctl.ALU_Src2 := ALUSrc2.imm
            out_comb.decode.imm := inst.immJ
            out_comb.exu_ctl.ALU_Control := ALUOp.add
            out_comb.exu_ctl.jump_op := JumpOp.jump
            out_comb.exu_ctl.reg_write := true.B
            out_comb.exu_ctl.wb_src := WBSrc.pc_plus4
            invalid := false.B
        }

        is("b1100011".U){// beq
            out_comb.exu_ctl.ALU_Src1 := ALUSrc1.pc
            out_comb.exu_ctl.ALU_Src2 := ALUSrc2.imm
            out_comb.decode.imm := inst.immB
            out_comb.exu_ctl.ALU_Control := ALUOp.add
            switch(inst.funct3){
                is("b000".U){
                    out_comb.exu_ctl.branch_op := BranchOp.beq
                    invalid := false.B
                }
                is("b001".U){
                    out_comb.exu_ctl.branch_op := BranchOp.bne
                    invalid := false.B
                }
                is("b100".U){
                    out_comb.exu_ctl.branch_op := BranchOp.blt
                    invalid := false.B                    
                }
                is("b101".U){
                    out_comb.exu_ctl.branch_op := BranchOp.bge
                    invalid := false.B                    
                }
                is("b110".U){
                    out_comb.exu_ctl.branch_op := BranchOp.bltu
                    invalid := false.B                    
                }
                is("b111".U){
                    out_comb.exu_ctl.branch_op := BranchOp.bgeu
                    invalid := false.B
                }
            }
        }
        is("b0001111".U){ //fence.i
            when(inst.funct3 === "b001".U){
                out_comb.exu_ctl.fence_i := true.B
                invalid := false.B
            }
        }
    }

    RFS_ReadPort.RegReadPort.raddr1 := inst.rs1
    RFS_ReadPort.RegReadPort.raddr2 := inst.rs2
    out_comb.rs1_data := RFS_ReadPort.RegReadPort.rdata1
    out_comb.rs2_data := RFS_ReadPort.RegReadPort.rdata2
    
    RFS_ReadPort.CSRReadPort.CSRRAddr := csr_read_addr
    out_comb.csr_rdata := RFS_ReadPort.CSRReadPort.CSRRData

    // 指令分类计数：基于被接受的指令（out_reg 内容），与 out.fire 同拍；
    // 不能用 in 侧 inst——out.fire 时 inst 已是下一条指令（out_reg 改造后的错位）
    val fired_inst = out_reg.decode.inst.asTypeOf(new InstDecode)
    PerfCounter("IDU_calc") := (fired_inst.opcode === "b0010011".U || fired_inst.opcode === "b0110011".U) && out.fire
    PerfCounter("IDU_jump") := (fired_inst.opcode === "b1100111".U || fired_inst.opcode === "b1101111".U) && out.fire
    PerfCounter("IDU_csr") := (fired_inst.opcode === "b1110011".U) && out.fire
    PerfCounter("IDU_load") := (fired_inst.opcode === "b0000011".U) && out.fire
    PerfCounter("IDU_store") := (fired_inst.opcode === "b0100011".U) && out.fire
    PerfCounter("IDU_branch") := (fired_inst.opcode === "b1100011".U) && out.fire
    // RAW 数据冒险导致的停顿拍数
    PerfCounter("IDU_stall_cycles") := stall
}