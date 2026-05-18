package npc
import chisel3._
import chisel3.util._
import chisel3.simulator.PeekPokeAPI.TestableData
import scopt.Read

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

class WriteRegPortBundle extends Bundle{
    val wdata  = Output(UInt(32.W))
    val waddr  = Output(UInt(5.W))
    val wen    = Output(Bool())

    val CSRAddr = Output(UInt(12.W))
    val CSRData = Output(UInt(32.W))
    val CSRWrite = Output(Bool())
}

class ReadRegPortBundle extends Bundle{

    val raddr1 = Output(UInt(5.W))
    val rdata1 = Input(UInt(32.W))
    val raddr2 = Output(UInt(5.W))
    val rdata2 = Input(UInt(32.W))

    val CSRAddr = Output(UInt(12.W))
    val CSRData = Input(UInt(32.W))
    
}

class RFS extends Module {
  val WritePort = IO(Flipped(new WriteRegPortBundle))

  val ReadPort = IO(Flipped(new ReadRegPortBundle))
    val Reg_u = Module(new RegBlackBox())
    Reg_u.io.wdata := WritePort.wdata
    Reg_u.io.waddr := WritePort.waddr
    Reg_u.io.wen := WritePort.wen
    Reg_u.io.raddr1 := ReadPort.raddr1
    Reg_u.io.raddr2 := ReadPort.raddr2
    ReadPort.rdata1 := Reg_u.io.rdata1
    ReadPort.rdata2 := Reg_u.io.rdata2
    Reg_u.io.clock := clock

    val mcycle = RegInit(0.U(64.W))

    ReadPort.CSRData := 0.U
    switch(ReadPort.CSRAddr){
      is("hB00".U){// mcycle
        ReadPort.CSRData := mcycle(31,0)
      }
      is("hB80".U){// mcycleh
        ReadPort.CSRData := mcycle(63,32)
      }
      is("hF11".U){// mvendorid
        ReadPort.CSRData := "h79737978".U
      }
      is("hF12".U){// marchid
        ReadPort.CSRData := "26030092".U
      }
    }
    mcycle := mcycle + 1.U
    when(WritePort.CSRWrite){
      switch(WritePort.CSRAddr){//mcycle
        is("hB00".U){
          mcycle := Cat(mcycle(63,32),WritePort.CSRData)
        }
        is("hB80".U){
          mcycle := Cat(WritePort.CSRData,mcycle(31,0))
        }
      }
    }
}