package npc
import chisel3._
import chisel3.util._
import chisel3.simulator.PeekPokeAPI.TestableData

class RegBlackBox extends ExtModule{
  val io = FlatIO(new Bundle {
    val clock = Input(Clock())
    val wdata  = Input(UInt(32.W))
    val waddr  = Input(UInt(5.W))
    val wen    = Input(Bool())
    val raddr1 = Input(UInt(5.W))
    val rdata1 = Output(UInt(32.W))
    val raddr2 = Input(UInt(5.W))
    val rdata2 = Output(UInt(32.W))
  })
  addResource("/RegBlackBox.v")
}

class RFS extends Module {
  val io = IO(new Bundle {
    val wdata  = Input(UInt(32.W))
    val waddr  = Input(UInt(5.W))
    val wen    = Input(Bool())
    val raddr1 = Input(UInt(5.W))
    val rdata1 = Output(UInt(32.W))
    val raddr2 = Input(UInt(5.W))
    val rdata2 = Output(UInt(32.W))
  })
    val reg = Module(new RegBlackBox())
    reg.io.wdata := io.wdata
    reg.io.waddr := io.waddr
    printf(p"RFS:${io.waddr}\n")
    reg.io.wen := io.wen
    reg.io.raddr1 := io.raddr1
    reg.io.raddr2 := io.raddr2
    io.rdata1 := reg.io.rdata1
    io.rdata2 := reg.io.rdata2
    reg.io.clock := clock
}