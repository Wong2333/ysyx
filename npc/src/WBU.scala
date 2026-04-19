package npc

import chisel3._
import chisel3.util._

class WBU extends Module{
    val io = IO(new Bundle {
        val pc = Input(UInt(32.W))
        val ALU_out = Input(UInt(32.W))
        val imm = Input(UInt(32.W))
        val mem_rdata = Input(UInt(32.W))
        val WB_sel = Input(UInt(3.W))
        val pc_sel = Input(UInt(2.W))

        val reg_wdata = Output(UInt(32.W))
        val pc_next = Output(UInt(32.W))
    })
    io.reg_wdata := 0.U
    switch(io.WB_sel){
        is(0.U){
            io.reg_wdata:=0.U
        }
        is(1.U){
            io.reg_wdata:=io.ALU_out
        }
        is(2.U){
            io.reg_wdata:=io.mem_rdata
        }
        is(3.U){
            io.reg_wdata:=io.pc+4.U
        }
        is(4.U){
            io.reg_wdata:=io.imm
        }
    }
    io.pc_next := 0.U
    switch(io.pc_sel){
        is(0.U){
            io.pc_next:=0.U
        }
        is(1.U){
            io.pc_next:=io.ALU_out
        }
    }
}