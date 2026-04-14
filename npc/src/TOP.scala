package npc

import chisel3._

class Top extends Module{

    val io = IO(new Bundle{
        val in = Input(UInt(8.W));
        val out = Output(UInt(3.W));
        val vaild = Output(Bool());
    });
    val encoder = Module(new PriorityEncoder(8));
    encoder.io <> io;
}