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
  val WritePort = IO(Flipped(new WriteRegPortBundle))

  val ReadPort = IO(Flipped(new ReadRegPortBundle))
  val CSRPort = IO(new Bundle {
    val WritePort = Flipped(new CSRWritePortBundle)
    val ReadPort = Flipped(new CSRReadPortBundle)
  })
  val ExceptionPort = IO(Flipped(new ExceptionInfoBundle))

    val PC_reg = Module(new PCBlackBox())
    PC_reg.io.clock := clock
    PC_reg.io.reset := reset
    PC_reg.io.pc_next := WritePort.PCNext
    PC_reg.io.pc_we := WritePort.PCWrite

    ReadPort.pc := PC_reg.io.pc
  
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

    val mepc = Reg(UInt(32.W))
    val mcause = Reg(UInt(32.W))
    val mtvec = Reg(UInt(32.W))
    val mstatus = RegInit("h1800".U(32.W))

    CSRPort.ReadPort.CSRRData := 0.U
    switch(CSRPort.ReadPort.CSRRAddr){
      is("hB00".U){// mcycle
        CSRPort.ReadPort.CSRRData := mcycle(31,0)
      }
      is("hB80".U){// mcycleh
        CSRPort.ReadPort.CSRRData := mcycle(63,32)
      }
      is("hF11".U){// mvendorid
        CSRPort.ReadPort.CSRRData := "h79737978".U
      }
      is("hF12".U){// marchid
        CSRPort.ReadPort.CSRRData := 26030092.U
      }
      is("h305".U){
        CSRPort.ReadPort.CSRRData := mtvec
      }
      is("h300".U){
        CSRPort.ReadPort.CSRRData := mstatus
      }
      is("h341".U){
        CSRPort.ReadPort.CSRRData := mepc
      }
      is("h342".U){
        CSRPort.ReadPort.CSRRData := mcause
      }
    }
    mcycle := mcycle + 1.U
    when(CSRPort.WritePort.CSRWrite){
      switch(CSRPort.WritePort.CSRWAddr){//mcycle
        is("hB00".U){
          mcycle := Cat(mcycle(63,32),CSRPort.WritePort.CSRWData)
        }
        is("hB80".U){
          mcycle := Cat(CSRPort.WritePort.CSRWData,mcycle(31,0))
        }
        is("h305".U){
          printf(cf"write mtvec: ${CSRPort.WritePort.CSRWData}\n")
          mtvec := CSRPort.WritePort.CSRWData
        }
        is("h300".U){
          mstatus := CSRPort.WritePort.CSRWData
        }
        is("h341".U){
          mepc := CSRPort.WritePort.CSRWData
        }
        is("h342".U){
          mcause := CSRPort.WritePort.CSRWData
        }
      }
    }

    when(ExceptionPort.isTrap){
      printf(cf"ecall to pc ${mtvec}")
      mepc := ExceptionPort.PC
      mcause := ExceptionPort.Exception_Code
      PC_reg.io.pc_next := mtvec
      PC_reg.io.pc_we := true.B
    }
}