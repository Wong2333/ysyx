package npc

import chisel3._

class Top extends Module{

    val io = IO(new Bundle{
        val rst = Input(Bool())
    })
    val IFU_u = Module(new IFU())
    
}