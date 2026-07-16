package test

import chisel3._
import chisel3.util._

class Bun extends Bundle{
    val A = FlatIO(new Bundle {
        val a = Input(Bool())
    })
    val B = FlatIO(new Bundle {
        val b = Output(Bool())
    })
}

class test extends Module{
    val io = IO(new Bun)
    io.B.b := io.A.a
}