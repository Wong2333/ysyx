package npc

import chisel3._
import chisel3.util._
import npc.BPU.BPUTrainBundle

class ysyx_26030092_BranchUnit extends Module{
    val flush      = IO(Output(Bool()))
    val redirectPC = IO(Output(UInt(32.W)))
    val bpu_train  = IO(Output(new BPUTrainBundle))

    val pc        = IO(Input(UInt(32.W)))
    val alu       = IO(Input(UInt(32.W)))
    val rs1       = IO(Input(UInt(32.W)))
    val rs2       = IO(Input(UInt(32.W)))
    val csr_rdata = IO(Input(UInt(32.W)))
    val mtvec     = IO(Input(UInt(32.W)))

    val branch_op = IO(Input(BranchOp()))
    val jump_op   = IO(Input(JumpOp()))
    val sys_op    = IO(Input(SysOp()))
    val is_excp   = IO(Input(Bool()))

    val valid    = IO(Input(Bool()))
    val fire     = IO(Input(Bool()))
    val lsu_trap = IO(Input(Bool()))

    val branch_cond = MuxLookup(branch_op, false.B)(Seq(
        BranchOp.beq  -> (rs1 === rs2),
        BranchOp.bne  -> (rs1 =/= rs2),
        BranchOp.blt  -> (rs1.asSInt <  rs2.asSInt),
        BranchOp.bge  -> (rs1.asSInt >= rs2.asSInt),
        BranchOp.bltu -> (rs1 <  rs2),
        BranchOp.bgeu -> (rs1 >= rs2)))

    val is_branch = branch_op =/= BranchOp.none
    val is_jump   = jump_op === JumpOp.jump
    val is_mret   = sys_op === SysOp.mret
    val taken     = branch_cond || is_jump

    val shadow_pc = Module(new PCBlackBox)
    val pc_hit    = shadow_pc.io.pc === pc
    val jump      = lsu_trap || ((taken || is_mret || is_excp) && fire && pc_hit)
    val next_pc   = Mux(lsu_trap, mtvec, MuxCase(0.U, Seq(
        taken   -> alu,
        is_mret -> csr_rdata,
        is_excp -> mtvec)))

    shadow_pc.io.clock   := clock
    shadow_pc.io.reset   := reset
    shadow_pc.io.pc_4    := fire && pc_hit && !lsu_trap
    shadow_pc.io.pc_we   := jump
    shadow_pc.io.pc_next := next_pc

    bpu_train.is_jal := is_jump
    bpu_train.pc     := shadow_pc.io.pc(31,2)
    bpu_train.valid  := (is_branch || is_jump) && fire && !lsu_trap
    bpu_train.taken  := taken
    bpu_train.target := next_pc(31,2)

    flush      := (valid && !pc_hit) || lsu_trap
    redirectPC := Mux(lsu_trap, mtvec, shadow_pc.io.pc)
}
