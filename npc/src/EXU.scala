package npc

import chisel3._
import chisel3.util._

class EXU extends Module{
    val io = IO(new Bundle {
        val imm = Input(UInt(32.W))
        val src1 = Input(UInt(32.W))
        val src2 = Input(UInt(32.W))
        val ALU_ctl = Input(UInt(4.W))

        val out = Output(UInt(32.W))
    })
    io.out := 0.U
    switch(io.ALU_ctl){
        is("b0001".U){
            io.out := io.src1 + io.imm
        }
        is("b0010".U){
            io.out := io.src1 + io.src2
        }
    }
}