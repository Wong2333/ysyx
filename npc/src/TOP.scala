package npc

import chisel3._

class Top extends Module{

    val io = IO(new Bundle{
        val rst = Input(Bool())
        val pc_next = Input(UInt(32.W))
        val pc_we = Input(Bool())
    })
    val IFU_u = Module(new IFU())
    IFU_u.io.rst := io.rst
    IFU_u.io.pc_next := io.pc_next
    IFU_u.io.pc_we := io.pc_we
}