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
        val pc_4 = Input(Bool())
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
}

class RFS extends Module {
  val WBU_in = IO(Flipped(Decoupled(new Bundle {
    val WriteRegPort = Flipped(new WriteRegPortBundle)
    val ExceptionPort = Flipped(new ExceptionInfoBundle)
    val CSRWritePort = Flipped(new CSRWritePortBundle)
  })))
  val RFS_ReadPort = IO(new Bundle {
    val RegReadPort = Flipped(new ReadRegPortBundle)
    val CSRReadPort = Flipped(new CSRReadPortBundle)
  })
  val PC_out = IO(Decoupled(new Bundle{
      val pc = Output(UInt(32.W))
    }))

    WBU_in.ready := true.B
    PC_out.valid := WBU_in.valid

    val PC_reg = Module(new PCBlackBox())
    PC_reg.io.clock := clock
    PC_reg.io.reset := reset
    PC_reg.io.pc_next := WBU_in.bits.WriteRegPort.PCNext
    PC_reg.io.pc_we := WBU_in.bits.WriteRegPort.PCWrite && WBU_in.valid
    PC_reg.io.pc_4 := WBU_in.valid
    PC_out.bits.pc := PC_reg.io.pc
  
    val Reg_u = Module(new RegBlackBox())
    Reg_u.io.wdata := WBU_in.bits.WriteRegPort.wdata
    Reg_u.io.waddr := WBU_in.bits.WriteRegPort.waddr
    Reg_u.io.wen := WBU_in.bits.WriteRegPort.wen && WBU_in.valid
    Reg_u.io.raddr1 := RFS_ReadPort.RegReadPort.raddr1
    Reg_u.io.raddr2 := RFS_ReadPort.RegReadPort.raddr2
    RFS_ReadPort.RegReadPort.rdata1 := Reg_u.io.rdata1
    RFS_ReadPort.RegReadPort.rdata2 := Reg_u.io.rdata2
    Reg_u.io.clock := clock

    val mcycle = RegInit(0.U(64.W))

    val mepc = Reg(UInt(32.W))
    val mcause = Reg(UInt(32.W))
    val mtvec = Reg(UInt(32.W))
    val mstatus = RegInit("h1800".U(32.W))

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
    mcycle := mcycle + 1.U
    when(WBU_in.bits.CSRWritePort.CSRWrite && WBU_in.valid){
      switch(WBU_in.bits.CSRWritePort.CSRWAddr){//mcycle
        is("hB00".U){
          mcycle := Cat(mcycle(63,32),WBU_in.bits.CSRWritePort.CSRWData)
        }
        is("hB80".U){
          mcycle := Cat(WBU_in.bits.CSRWritePort.CSRWData,mcycle(31,0))
        }
        is("h305".U){
          mtvec := WBU_in.bits.CSRWritePort.CSRWData
        }
        is("h300".U){
          mstatus := WBU_in.bits.CSRWritePort.CSRWData
        }
        is("h341".U){
          mepc := WBU_in.bits.CSRWritePort.CSRWData
        }
        is("h342".U){
          mcause := WBU_in.bits.CSRWritePort.CSRWData
        }
      }
    }

    when(WBU_in.bits.ExceptionPort.isTrap && WBU_in.valid){
      mepc := WBU_in.bits.ExceptionPort.PC
      mcause := WBU_in.bits.ExceptionPort.Exception_Code
      PC_reg.io.pc_next := mtvec
      PC_reg.io.pc_we := true.B
    }
}