package npc
import chisel3._
import chisel3.util._

class ReadRegBlackBox extends ExtModule{
    val io = FlatIO(new Bundle {
        val addr = Input(UInt(32.W))
        // val data = Output(UInt(32.W))
    })
    addResource("/ReadRegBlackBox.v")
}

class RFS extends Module{
    val io = IO(new Bundle {
        val wdata = Input(UInt(32.W))
        val waddr = Input(UInt(5.W))
        val wen = Input(Bool())
        val raddr1 = Input(UInt(5.W))
        val raddr2 = Input(UInt(5.W))

        val rdata1 = Output(UInt(32.W))
        val rdata2 = Output(UInt(32.W))        
    })
    val ReadReg = Module(new ReadRegBlackBox())
    val rf = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))

    io.rdata1 := Mux(io.raddr1 === 0.U,0.U,rf(io.raddr1))
    io.rdata2 := Mux(io.raddr1 === 0.U,0.U,rf(io.raddr2))

    when(io.wen){
        rf(io.waddr) := io.wdata
    }
}