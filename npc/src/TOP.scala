package npc

import chisel3._

class Top extends Module{

    val io = IO(new Bundle{
        val clrn = Input(Bool())
        val ps2_clk = Input(Bool())
        val nextdata_n = Input(Bool())
        val ps2_data = Input(UInt(1.W))
        val data = Output(UInt(8.W))
        val ready = Output(Bool())
        val overflow = Output(Bool())
    })
    val keyboard = Module(new KeyBoard())
    keyboard.io <> io


}