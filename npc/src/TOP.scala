package npc

import chisel3._

class Top extends Module{

    val io = IO(new Bundle{
        val rst = Input(Bool())
        val pc_next = Input(UInt(32.W))
        val pc_we = Input(Bool())
        val pc = Output(UInt(32.W))
        val inst = Output(UInt(32.W))
    })
    val IFU_u = Module(new IFU())
    IFU_u.io <> io
}