package npc
import chisel3._
import chisel3.util._

class RFS extends ExtModule {
  val io = IO(new Bundle {
    val clk    = Input(Clock())
    val wdata  = Input(UInt(32.W))
    val waddr  = Input(UInt(5.W))
    val wen    = Input(Bool())
    val raddr1 = Input(UInt(5.W))
    val rdata1 = Output(UInt(32.W))
    val raddr2 = Input(UInt(5.W))
    val rdata2 = Output(UInt(32.W))
  })
  // 挂载 Verilog 文件
  addResource("/RegBlackBox.v")
}