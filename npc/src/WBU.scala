package npc

import chisel3._
import chisel3.util._

class WriteBackPortBundle extends Bundle{
    val PCNext = Output(UInt(32.W))
    val JumpOrBranch = Output(Bool())
    val RegWaddr = Output(UInt(5.W))
    val RegWdata = Output(UInt(32.W))
    val RegWrite = Output(Bool())
}

class WBU extends Module{
    val in = IO(Flipped(new WriteBackPortBundle))

    val out = IO(new Bundle {
        val IFUBundle = Output(new Bundle {
            val pc_next = Input(UInt(32.W))
            val pc_we = Input(Bool())
        })
        val RFSBundle = Output(new Bundle {
            val wdata  = Input(UInt(32.W))
            val waddr  = Input(UInt(5.W))
            val wen    = Input(Bool())
        })

    })

    out.IFUBundle.pc_next := in.PCNext
    out.IFUBundle.pc_we := in.JumpOrBranch
    out.RFSBundle.waddr := in.RegWaddr
    out.RFSBundle.wdata := in.RegWdata
    out.RFSBundle.wen := in.RegWrite
    // val io = IO(new Bundle {
    //     val pc = Input(UInt(32.W))
    //     val ALU_out = Input(UInt(32.W))
    //     val imm = Input(UInt(32.W))
    //     val mem_rdata = Input(UInt(32.W))
    //     val WB_sel = Input(WB_CTL())
    //     val pc_sel = Input(PC_CTL())

    //     val reg_wdata = Output(UInt(32.W))
    //     val pc_next = Output(UInt(32.W))
    // })
    // io.reg_wdata := 0.U
    // switch(io.WB_sel){
    //     is(WB_CTL.ALU2Reg){
    //         io.reg_wdata:=io.ALU_out
    //     }
    //     is(WB_CTL.Mem2Reg){
    //         io.reg_wdata:=io.mem_rdata
    //     }
    //     is(WB_CTL.PCNext2Reg){
    //         io.reg_wdata:=io.pc+4.U
    //     }
    //     is(WB_CTL.Imm2Reg){
    //         io.reg_wdata:=io.imm
    //     }
    // }
    // io.pc_next := 0.U
    // switch(io.pc_sel){
    //     is(PC_CTL.ALU2PC){
    //         io.pc_next:=io.ALU_out
    //     }
    // }
}