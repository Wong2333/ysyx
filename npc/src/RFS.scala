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
    val Reg_u = Module(new RegBlackBox())
    Reg_u.io.wdata := io.wdata
    Reg_u.io.waddr := io.waddr
    Reg_u.io.wen := io.wen
    Reg_u.io.raddr1 := io.raddr1
    Reg_u.io.raddr2 := io.raddr2
    io.rdata1 := Reg_u.io.rdata1
    io.rdata2 := Reg_u.io.rdata2
    Reg_u.io.clock := clock
}