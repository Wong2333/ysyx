package npc.EXU

import chisel3._
import chisel3.util._
import npc.LSUBundle

class LSMainPipe extends Module{
    val req = IO(Decoupled(new LSUBundle))
    val resp = IO(Decoupled(new Bundle {
        val rdata = Output(UInt(32.W))
        val error = Output(Bool())
    }))
    
}