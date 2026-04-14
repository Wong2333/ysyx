package npc

import chisel3._
import chisel3.util._

class LFSR8 extends Module{
    val io = IO(new Bundle{
        val enable = Input(Bool())
        val out = Output(UInt(8.W))
    })
    val reg = RegInit(1.U(8.W))

    when(reg === 0.U){
        reg := 1.U
    }.otherwise{
        val feedback = reg(4) ^ reg(3) ^ reg(2) ^ reg(0)
        reg := Cat(feedback,reg(7,1))
        printf(p"[SHIFT] Old: ${reg}%x, New feedback:${feedback}%b\n")
    }
    
    io.out := reg
}