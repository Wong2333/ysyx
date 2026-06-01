package npc
import chisel3._
import chisel3.util._

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

class PCBlackBox extends ExtModule{
    val io = FlatIO(new Bundle {
        val clock = Input(Clock())
        val reset = Input(Reset())
        val pc_next = Input(UInt(32.W))
        val pc_we = Input(Bool())
        val pc = Output(UInt(32.W))
    })
    addResource("/PCBlackBox.v")
}

class WriteRegPortBundle extends Bundle{
    val wdata  = Output(UInt(32.W))
    val waddr  = Output(UInt(5.W))
    val wen    = Output(Bool())

    val PCNext = Output(UInt(32.W))
    val PCWrite = Output(Bool())
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
    val pc = Input(UInt(32.W))
}

class RFS extends Module {
  val WBU_in = IO(new Bundle {
    val WriteRegPort = Flipped(new WriteRegPortBundle)
    val ExceptionPort = Flipped(new ExceptionInfoBundle)
    val CSRWritePort = Flipped(new CSRWritePortBundle)
  })
  val ReadPort = IO(Flipped(new ReadRegPortBundle))
  val CSRReadPort = IO(Flipped(new CSRReadPortBundle))


    val PC_reg = Module(new PCBlackBox())
    PC_reg.io.clock := clock
    PC_reg.io.reset := reset
    PC_reg.io.pc_next := WBU_in.WriteRegPort.PCNext
    PC_reg.io.pc_we := WBU_in.WriteRegPort.PCWrite

    ReadPort.pc := PC_reg.io.pc
  
    val Reg_u = Module(new RegBlackBox())
    Reg_u.io.wdata := WBU_in.WriteRegPort.wdata
    Reg_u.io.waddr := WBU_in.WriteRegPort.waddr
    Reg_u.io.wen := WBU_in.WriteRegPort.wen
    Reg_u.io.raddr1 := ReadPort.raddr1
    Reg_u.io.raddr2 := ReadPort.raddr2
    ReadPort.rdata1 := Reg_u.io.rdata1
    ReadPort.rdata2 := Reg_u.io.rdata2
    Reg_u.io.clock := clock

    val mcycle = RegInit(0.U(64.W))

    val mepc = Reg(UInt(32.W))
    val mcause = Reg(UInt(32.W))
    val mtvec = Reg(UInt(32.W))
    val mstatus = RegInit("h1800".U(32.W))

    CSRReadPort.CSRRData := 0.U
    switch(CSRReadPort.CSRRAddr){
      is("hB00".U){// mcycle
        CSRReadPort.CSRRData := mcycle(31,0)
      }
      is("hB80".U){// mcycleh
        CSRReadPort.CSRRData := mcycle(63,32)
      }
      is("hF11".U){// mvendorid
        CSRReadPort.CSRRData := "h79737978".U
      }
      is("hF12".U){// marchid
        CSRReadPort.CSRRData := 26030092.U
      }
      is("h305".U){
        CSRReadPort.CSRRData := mtvec
      }
      is("h300".U){
        CSRReadPort.CSRRData := mstatus
      }
      is("h341".U){
        CSRReadPort.CSRRData := mepc
      }
      is("h342".U){
        CSRReadPort.CSRRData := mcause
      }
    }
    mcycle := mcycle + 1.U
    when(WBU_in.CSRWritePort.CSRWrite){
      switch(WBU_in.CSRWritePort.CSRWAddr){//mcycle
        is("hB00".U){
          mcycle := Cat(mcycle(63,32),WBU_in.CSRWritePort.CSRWData)
        }
        is("hB80".U){
          mcycle := Cat(WBU_in.CSRWritePort.CSRWData,mcycle(31,0))
        }
        is("h305".U){
          mtvec := WBU_in.CSRWritePort.CSRWData
        }
        is("h300".U){
          mstatus := WBU_in.CSRWritePort.CSRWData
        }
        is("h341".U){
          mepc := WBU_in.CSRWritePort.CSRWData
        }
        is("h342".U){
          mcause := WBU_in.CSRWritePort.CSRWData
        }
      }
    }

    when(WBU_in.ExceptionPort.isTrap){
      mepc := WBU_in.ExceptionPort.PC
      mcause := WBU_in.ExceptionPort.Exception_Code
      PC_reg.io.pc_next := mtvec
      PC_reg.io.pc_we := true.B
    }
}