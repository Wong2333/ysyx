package npc

import chisel3._
import chisel3.util._
import npc.PmemReadBlackBox

object MEM_CTL extends ChiselEnum{
    val none,
    Read4B,
    Read1B,
    Read1BU,
    Write4B,
    Write1B = Value
}

class MemPortBundle extends Bundle{
    val valid = Output(Bool())
    val mem_ctl = Output(MEM_CTL())
    val wdata = Output(UInt(32.W))
    val waddr = Output(UInt(32.W))
    val raddr = Output(UInt(32.W))
    val rdata = Input(UInt(32.W))
}

class PmemWriteBlackBox extends ExtModule {
    val io = FlatIO(new Bundle {
        val clock = Input(Clock())
        val waddr = Input(UInt(32.W))
        val wdata = Input(UInt(32.W))
        val wmask = Input(UInt(4.W))
        val we = Input(Bool())
    })
    addResource("/PmemWriteBlackBox.v")
}
class LSU extends Module{
    val io = IO(Flipped(new MemPortBundle))

    
    val mask = WireDefault(0.U(4.W))
    val waddr = WireDefault(0.U)
    val wdata = WireDefault(0.U(32.W))
    val wen = WireDefault(false.B)

    val shifted_rdata = WireDefault(0.U(32.W))
    val shifted_wdata = WireDefault(0.U(32.W))

    val pmem_read = Module(new PmemReadBlackBox())
    val raddr = WireDefault(0.U(32.W))
    val rdata = WireDefault(0.U(32.W))
    pmem_read.io.raddr  := raddr 
    rdata := pmem_read.io.rdata

    val pmem_write = Module(new PmemWriteBlackBox())
    pmem_write.io.clock := clock
    pmem_write.io.waddr := waddr
    pmem_write.io.wdata := wdata
    pmem_write.io.wmask := mask
    pmem_write.io.we := wen

    io.rdata := 0.U

    when(io.valid){
        raddr := io.raddr & ~3.U(32.W)
        shifted_rdata := rdata >> (io.raddr(1,0) << 3)
        shifted_wdata := io.wdata << (io.waddr(1,0) << 3)
        switch(io.mem_ctl){
            is(MEM_CTL.Read4B){
                io.rdata := rdata
            }
            is(MEM_CTL.Read1B){
                io.rdata := Cat(Fill(24, shifted_rdata(7)), shifted_rdata(7, 0))
            }
            is(MEM_CTL.Read1BU){
                io.rdata := Cat(Fill(24,0.U),shifted_rdata(7, 0))
            }
            is(MEM_CTL.Write4B){
                mask := "b1111".U;
                wdata := io.wdata;
                waddr := io.waddr & ~3.U(32.W)
                wen := true.B
            }
            is(MEM_CTL.Write1B){
                mask := 1.U << io.waddr(1,0)
                wdata := shifted_wdata
                waddr := io.waddr & ~3.U(32.W)
                wen := true.B
            }
        }
    }


}