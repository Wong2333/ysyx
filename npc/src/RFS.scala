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
  val WritePort = IO(new Bundle {
    val wdata  = Input(UInt(32.W))
    val waddr  = Input(UInt(5.W))
    val wen    = Input(Bool())
  })

  val ReadPort = IO(new Bundle {
    val raddr1 = Input(UInt(5.W))
    val rdata1 = Output(UInt(32.W))
    val raddr2 = Input(UInt(5.W))
    val rdata2 = Output(UInt(32.W))
  })
    val Reg_u = Module(new RegBlackBox())
    Reg_u.io.wdata := WritePort.wdata
    Reg_u.io.waddr := WritePort.waddr
    Reg_u.io.wen := WritePort.wen
    Reg_u.io.raddr1 := ReadPort.raddr1
    Reg_u.io.raddr2 := ReadPort.raddr2
    ReadPort.rdata1 := Reg_u.io.rdata1
    ReadPort.rdata2 := Reg_u.io.rdata2
    Reg_u.io.clock := clock
}