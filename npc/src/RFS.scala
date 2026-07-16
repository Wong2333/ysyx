package npc
import chisel3._
import chisel3.util._
import npc.MemControl.sw
import scala.annotation.varargs

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

class ysyx_26030092_RFS extends Module {
  val wbu_in = IO(Flipped(Decoupled(new Bundle {
    val WriteRegPort = Flipped(new WriteRegPortBundle)
    val ExceptionPort = Flipped(new ExceptionInfoBundle)
    val CSRWritePort = Flipped(new CSRWritePortBundle)
  })))
  val RFS_ReadPort = IO(new Bundle {
    val RegReadPort = Flipped(new ReadRegPortBundle)
    val CSRReadPort = Flipped(new CSRReadPortBundle)
  })
  val pc_out = IO(Decoupled(new Bundle{
      val pc = Output(UInt(32.W))
    }))

    wbu_in.ready := false.B
    pc_out.valid := false.B
    
    val s_idle :: s_wait_write_back :: Nil = Enum(2)
    val status = RegInit(s_idle)
    printf("Instance path: %m\n")

    switch(status){
      is(s_idle){
        pc_out.valid := true.B
        when(pc_out.ready){
          status := s_wait_write_back
        }
      }
      is(s_wait_write_back){
        wbu_in.ready := true.B
        when(wbu_in.fire){
          status := s_idle
        }
      }
    }

    val PC_reg = Module(new PCBlackBox())
    PC_reg.io.clock := clock
    PC_reg.io.reset := reset
    pc_out.bits.pc := PC_reg.io.pc
    PC_reg.io.pc_next := DontCare
    PC_reg.io.pc_we := false.B
    PC_reg.io.pc_4 := false.B

    when(wbu_in.fire){
      PC_reg.io.pc_next := wbu_in.bits.WriteRegPort.PCNext
      PC_reg.io.pc_we := wbu_in.bits.WriteRegPort.PCWrite
      PC_reg.io.pc_4 := !wbu_in.bits.WriteRegPort.PCWrite
    }

  
    val Reg_u = Module(new RegBlackBox())
    Reg_u.io.raddr1 := RFS_ReadPort.RegReadPort.raddr1
    Reg_u.io.raddr2 := RFS_ReadPort.RegReadPort.raddr2
    RFS_ReadPort.RegReadPort.rdata1 := Reg_u.io.rdata1
    RFS_ReadPort.RegReadPort.rdata2 := Reg_u.io.rdata2
    Reg_u.io.wdata := wbu_in.bits.WriteRegPort.wdata
    Reg_u.io.waddr := wbu_in.bits.WriteRegPort.waddr
    Reg_u.io.wen := false.B
    Reg_u.io.clock := clock

    when(wbu_in.fire){
      Reg_u.io.wen := wbu_in.bits.WriteRegPort.wen
    }



    val mcycle = RegInit(0.U(64.W))
    mcycle := mcycle + 1.U
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
    
    when(wbu_in.bits.CSRWritePort.CSRWrite && wbu_in.fire){
      switch(wbu_in.bits.CSRWritePort.CSRWAddr){//mcycle
        is("hB00".U){
          mcycle := Cat(mcycle(63,32),wbu_in.bits.CSRWritePort.CSRWData)
        }
        is("hB80".U){
          mcycle := Cat(wbu_in.bits.CSRWritePort.CSRWData,mcycle(31,0))
        }
        is("h305".U){
          mtvec := wbu_in.bits.CSRWritePort.CSRWData
        }
        is("h300".U){
          mstatus := wbu_in.bits.CSRWritePort.CSRWData
        }
        is("h341".U){
          mepc := wbu_in.bits.CSRWritePort.CSRWData
        }
        is("h342".U){
          mcause := wbu_in.bits.CSRWritePort.CSRWData
        }
      }
    }

    when(wbu_in.bits.ExceptionPort.isTrap && wbu_in.fire){
      mepc := wbu_in.bits.ExceptionPort.PC
      mcause := wbu_in.bits.ExceptionPort.Exception_Code
      PC_reg.io.pc_next := mtvec
      PC_reg.io.pc_we := true.B
    }
}