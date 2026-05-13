package npc

import chisel3._
import chisel3.util._

object WB_CTL extends ChiselEnum{
    val none = Value
    val ALU2Reg = Value("b1".U)
    val Mem2Reg,PCNext2Reg,Imm2Reg = Value
}

object PC_CTL extends ChiselEnum{
    val none = Value
    val ALU2PC = Value("b1".U)
    val ukn = Value("b11".U)
}

class WBU extends Module{
    val io = IO(new Bundle {
        val pc = Input(UInt(32.W))
        val ALU_out = Input(UInt(32.W))
        val imm = Input(UInt(32.W))
        val mem_rdata = Input(UInt(32.W))
        val WB_sel = Input(WB_CTL())
        val pc_sel = Input(PC_CTL())

        val reg_wdata = Output(UInt(32.W))
        val pc_next = Output(UInt(32.W))
    })
    io.reg_wdata := 0.U
    switch(io.WB_sel){
        is(WB_CTL.ALU2Reg){
            io.reg_wdata:=io.ALU_out
        }
        is(WB_CTL.Mem2Reg){
            io.reg_wdata:=io.mem_rdata
        }
        is(WB_CTL.PCNext2Reg){
            io.reg_wdata:=io.pc+4.U
            printf(cf"write back to reg, pc next: ${io.pc+4.U}\n")
        }
        is(WB_CTL.Imm2Reg){
            io.reg_wdata:=io.imm
        }
    }
    io.pc_next := 0.U
    switch(io.pc_sel){
        is(PC_CTL.ALU2PC){
            io.pc_next:=io.ALU_out
            printf(cf"write back to pc ALU out: ${io.ALU_out}\n")
        }
    }
}