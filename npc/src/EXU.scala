package npc

import chisel3._
import chisel3.util._
import npc.EXUs._
import npc.PMU._
import npc.BPU.BPUTrainBundle

object JumpOp extends ChiselEnum{
    val none, jump = Value
}

object MemOp extends ChiselEnum{
    val none, load, store = Value
}

object MemSize extends ChiselEnum{
    val byte, half, word = Value
}

object WBSrc extends ChiselEnum{
    val none, alu, mem, pc_plus4, csr = Value
}

object BranchOp extends ChiselEnum{
    val none, beq, bne, blt, bge, bltu, bgeu = Value
}

object CSROp extends ChiselEnum{
    val none, csrrw, csrrs = Value
}

object SysOp extends ChiselEnum{
    val none, mret = Value
}

class EXUCTLBundle extends Bundle{
    val ALU_Src1    = Output(ALUSrc1())
    val ALU_Src2    = Output(ALUSrc2())
    val ALU_Control = Output(ALUOp())
    val reg_write   = Output(Bool())
    val wb_src      = Output(WBSrc())
    val mem_op      = Output(MemOp())
    val mem_size    = Output(MemSize())
    val mem_signed  = Output(Bool())
    val branch_op   = Output(BranchOp())
    val jump_op     = Output(JumpOp())
    val csr_op      = Output(CSROp())
    val sys_op      = Output(SysOp())
    val fence_i     = Output(Bool())
}

class ysyx_26030092_EXU extends Module{
    val flush = IO(Output(Bool()))
    val redirectPC = IO(Output(UInt(32.W)))
    val mtvec = IO(Input(UInt(32.W)))

    val bpu_train = IO(Output(new BPUTrainBundle))

    val idu_out = IO(Flipped(Decoupled(new IDUBundle)))
    val idu_out_valid = IO(Input(Bool()))
    val wbu_in = IO(Decoupled(new WriteBackPortBundle))

    val lsu_in = IO(Decoupled(new LSUBundle))
    val lsu_out = IO(Flipped(Decoupled(new LSUOutBundle)))

    val commit_info = IO(Output(new CommitInfoBundle))

    val wb_info  = IO(Output(new StageWriteInfoBundle))
    val csr_info = IO(Output(new CSRWriteInfoBundle))

    val wb_slot        = Reg(new WriteBackPortBundle)
    val wb_slot_valid  = RegInit(false.B)
    val wb_slot_commit = Reg(new CommitInfoBundle)
    val lsu_busy       = RegInit(false.B)

    val lsu_trap = lsu_out.fire && lsu_out.bits.is_excp

    idu_out.ready := false.B
    lsu_out.ready := false.B
    lsu_in.valid := false.B
    wbu_in.valid := false.B

    val is_mem = idu_out.bits.exu_ctl.mem_op =/= MemOp.none

    val src1 = WireDefault(0.U(32.W))
    switch(idu_out.bits.exu_ctl.ALU_Src1){
        is(ALUSrc1.rs1){ src1 := idu_out.bits.rs1_data }
        is(ALUSrc1.pc) { src1 := idu_out.bits.decode.pc }
    }
    val src2 = WireDefault(0.U(32.W))
    switch(idu_out.bits.exu_ctl.ALU_Src2){
        is(ALUSrc2.rs2)   { src2 := idu_out.bits.rs2_data }
        is(ALUSrc2.imm)   { src2 := idu_out.bits.decode.imm }
        is(ALUSrc2.shamt) { src2 := Cat(0.U(27.W), idu_out.bits.decode.imm(4,0)) }
        is(ALUSrc2.csr)   { src2 := idu_out.bits.csr_rdata }
    }
    val ALU_u = Module(new ysyx_26030092_ALU)
    ALU_u.in.ctl := idu_out.bits.exu_ctl.ALU_Control
    ALU_u.in.src1 := src1
    ALU_u.in.src2 := src2

    val AluResult = WireDefault(0.U(32.W))
    dontTouch(AluResult)
    AluResult := ALU_u.out
    val PCAddFour = idu_out.bits.decode.pc + 4.U

    val branch_u = Module(new ysyx_26030092_BranchUnit)
    branch_u.pc        := idu_out.bits.decode.pc
    branch_u.alu       := AluResult
    branch_u.rs1       := idu_out.bits.rs1_data
    branch_u.rs2       := idu_out.bits.rs2_data
    branch_u.csr_rdata := idu_out.bits.csr_rdata
    branch_u.mtvec     := mtvec
    branch_u.branch_op := idu_out.bits.exu_ctl.branch_op
    branch_u.jump_op   := idu_out.bits.exu_ctl.jump_op
    branch_u.sys_op    := idu_out.bits.exu_ctl.sys_op
    branch_u.is_excp   := idu_out.bits.is_excp
    branch_u.valid     := idu_out_valid
    branch_u.fire      := idu_out.fire
    branch_u.lsu_trap  := lsu_trap

    flush      := branch_u.flush
    redirectPC := branch_u.redirectPC
    bpu_train  := branch_u.bpu_train

    // 构建wb wire
    val wb_data = Wire(new WriteBackPortBundle)
    wb_data := 0.U.asTypeOf(new WriteBackPortBundle)
    wb_data.RegWaddr := idu_out.bits.decode.rd
    wb_data.RegWrite := idu_out.bits.exu_ctl.reg_write
    wb_data.PC := idu_out.bits.decode.pc
    switch(idu_out.bits.exu_ctl.wb_src){
        is(WBSrc.alu)      { wb_data.RegWdata := AluResult }
        is(WBSrc.pc_plus4) { wb_data.RegWdata := PCAddFour }
        is(WBSrc.csr)      { wb_data.RegWdata := idu_out.bits.csr_rdata }
    }
    when(idu_out.bits.exu_ctl.csr_op =/= CSROp.none){
        wb_data.CSRAddr := idu_out.bits.decode.imm(11,0)
        wb_data.CSRWrite := true.B
        wb_data.CSRData := Mux(idu_out.bits.exu_ctl.csr_op === CSROp.csrrw,
                               idu_out.bits.rs1_data, AluResult)
    }
    wb_data.icache_flush := idu_out.bits.exu_ctl.fence_i
    wb_data.Exception_Code := idu_out.bits.excp_code
    wb_data.isTrap := idu_out.bits.is_excp

    // lsu接线
    lsu_in.bits := 0.U.asTypeOf(new LSUBundle)
    lsu_in.bits.addr := AluResult
    lsu_in.bits.wdata := idu_out.bits.rs2_data
    lsu_in.bits.mem_op := idu_out.bits.exu_ctl.mem_op
    lsu_in.bits.mem_size := idu_out.bits.exu_ctl.mem_size
    lsu_in.bits.mem_signed := idu_out.bits.exu_ctl.mem_signed
    lsu_in.bits.pc := idu_out.bits.decode.pc

    val commit_info_comb = Wire(new CommitInfoBundle)
    commit_info_comb.mem := is_mem
    commit_info_comb.mem_addr := AluResult
    commit_info_comb.pc := idu_out.bits.decode.pc

    // 非访存指令可以流水
    lsu_in.valid  := idu_out.valid && is_mem && !lsu_busy
    idu_out.ready := !lsu_busy && Mux(is_mem, lsu_in.ready, true.B)
    lsu_out.ready := lsu_busy
    wbu_in.valid  := wb_slot_valid
    wbu_in.bits   := wb_slot
    commit_info   := wb_slot_commit

    wb_slot_valid := false.B
    when(lsu_busy){ // EXU FSM 
        when(lsu_out.fire){
            wb_slot.RegWdata := lsu_out.bits.rdata
            wb_slot_valid    := true.B
            lsu_busy         := false.B
            when(lsu_out.bits.is_excp){
                wb_slot.isTrap         := true.B
                wb_slot.Exception_Code := lsu_out.bits.excp_code
                wb_slot.RegWrite       := false.B
                wb_slot.CSRWrite       := false.B
            }
        }
    }.elsewhen(idu_out.fire){
        wb_slot        := wb_data
        wb_slot_commit := commit_info_comb
        when(is_mem){ lsu_busy := true.B }.otherwise{ wb_slot_valid := true.B }
    }

    wb_info.wen   := (wb_slot_valid || lsu_busy) && wb_slot.RegWrite
    wb_info.rd    := wb_slot.RegWaddr
    wb_info.value := wb_slot.RegWdata
    wb_info.valid := wb_slot_valid && wb_slot.RegWrite

    csr_info.wen   := (wb_slot_valid || lsu_busy) && wb_slot.CSRWrite
    csr_info.addr  := wb_slot.CSRAddr
    csr_info.value := wb_slot.CSRData
    csr_info.valid := wb_slot_valid && wb_slot.CSRWrite

    PerfCounter("EXU_flush_count")         := flush
    PerfCounter("EXU_stall_cycles")        := idu_out.valid && !idu_out.ready
    PerfCounter("EXU_lsu_wait")            := lsu_busy
    PerfCounter("EXU_stall_by_lsu_reject") := idu_out.valid && is_mem && !lsu_in.ready
    PerfCounter("EXU_idle_cycles")         := !idu_out.valid && !wb_slot_valid && !lsu_busy
}
