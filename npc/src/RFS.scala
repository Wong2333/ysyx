package npc
import chisel3._
import chisel3.util._

class WriteRegPortBundle extends Bundle{
    val wdata  = Output(UInt(32.W))
    val waddr  = Output(UInt(5.W))
    val wen    = Output(Bool())
}

class CSRWritePortBundle extends Bundle{
    val CSRWAddr = Output(UInt(12.W))
    val CSRWData = Output(UInt(32.W))
    val CSRWrite = Output(Bool())

}
class CSRReadPortBundle extends Bundle{
    val CSRRAddr = Output(UInt(12.W))
    val CSRRData = Input(UInt(32.W))

}
class ExceptionInfoBundle extends Bundle{
    val isTrap = Output(Bool())
    val PC = Output(UInt(32.W))
    val Exception_Code = Output(UInt(32.W))
}

class ReadRegPortBundle extends Bundle{

    val raddr1 = Output(UInt(5.W))
    val rdata1 = Input(UInt(32.W))
    val raddr2 = Output(UInt(5.W))
    val rdata2 = Input(UInt(32.W))
}

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

class ysyx_26030092_RFS extends Module {
    val RFS_ReadPort = IO(new Bundle {
        val RegReadPort = Flipped(new ReadRegPortBundle)
        val CSRReadPort = Flipped(new CSRReadPortBundle)
    })

    val WriteRegPort = IO(Input(new WriteRegPortBundle))
    val CSRWritePort = IO(Input(new CSRWritePortBundle))
    val ExceptionPort = IO(Input(new ExceptionInfoBundle))

    val mtvec_out = IO(Output(UInt(32.W)))

    val Reg_u = Module(new RegBlackBox())
    Reg_u.io.raddr1 := RFS_ReadPort.RegReadPort.raddr1
    Reg_u.io.raddr2 := RFS_ReadPort.RegReadPort.raddr2
    RFS_ReadPort.RegReadPort.rdata1 := Reg_u.io.rdata1
    RFS_ReadPort.RegReadPort.rdata2 := Reg_u.io.rdata2
    Reg_u.io.wdata := WriteRegPort.wdata
    Reg_u.io.waddr := WriteRegPort.waddr
    Reg_u.io.wen := WriteRegPort.wen
    Reg_u.io.clock := clock

    val mcycle = RegInit(0.U(64.W))
    mcycle := mcycle + 1.U
    val mepc = RegInit(0.U(32.W))
    val mcause = RegInit(0.U(32.W))
    val mtvec = RegInit(0.U(32.W))
    val mstatus = RegInit("h1800".U(32.W))

    mtvec_out := mtvec

    RFS_ReadPort.CSRReadPort.CSRRData := 0.U
    switch(RFS_ReadPort.CSRReadPort.CSRRAddr){
      is("hB00".U){// mcycle
        RFS_ReadPort.CSRReadPort.CSRRData := mcycle(31,0)
      }
      is("hB80".U){// mcycleh
        RFS_ReadPort.CSRReadPort.CSRRData := mcycle(63,32)
      }
      is("hF11".U){// mvendorid
        RFS_ReadPort.CSRReadPort.CSRRData := "h79737978".U
      }
      is("hF12".U){// marchid
        RFS_ReadPort.CSRReadPort.CSRRData := 26030092.U
      }
      is("h305".U){
        RFS_ReadPort.CSRReadPort.CSRRData := mtvec
      }
      is("h300".U){
        RFS_ReadPort.CSRReadPort.CSRRData := mstatus
      }
      is("h341".U){
        RFS_ReadPort.CSRReadPort.CSRRData := mepc
      }
      is("h342".U){
        RFS_ReadPort.CSRReadPort.CSRRData := mcause
      }
    }

    when(CSRWritePort.CSRWrite){
      switch(CSRWritePort.CSRWAddr){//mcycle
        is("hB00".U){
          mcycle := Cat(mcycle(63,32),CSRWritePort.CSRWData)
        }
        is("hB80".U){
          mcycle := Cat(CSRWritePort.CSRWData,mcycle(31,0))
        }
        is("h305".U){
          mtvec := CSRWritePort.CSRWData
        }
        is("h300".U){
          mstatus := CSRWritePort.CSRWData
        }
        is("h341".U){
          mepc := CSRWritePort.CSRWData
        }
        is("h342".U){
          mcause := CSRWritePort.CSRWData
        }
      }
    }

    when(ExceptionPort.isTrap){
      mepc := ExceptionPort.PC
      mcause := ExceptionPort.Exception_Code
    }
}
