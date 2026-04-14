package npc

import chisel3._

class Top extends Module{

    val io = IO(new Bundle{
        val in1 = Input(UInt(4.W))
        val in2 = Input(UInt(4.W))
        val aluOp = Input(UInt(3.W))

        val out = Output(UInt(4.W))
        val zero = Output(Bool())
        val overflow = Output(Bool())
        val carry = Output(Bool())
    });
    val encoder = Module(new ALU(4));
    encoder.io <> io;
}