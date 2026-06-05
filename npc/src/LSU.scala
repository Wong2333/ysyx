package npc

import chisel3._
import chisel3.util._
import npc.PmemReadBlackBox

object MEM_CTL extends ChiselEnum{
    val none,
    Read4B,
    Read1B,
    Read1BU,
    Read2B,
    Read2BU,
    Write4B,
    Write1B,
    Write2B = Value
}

class MemPortBundle extends Bundle{
    val valid = Output(Bool())
    val mem_ctl = Output(MEM_CTL())
    val wdata = Output(UInt(32.W))
    val waddr = Output(UInt(32.W))
    val raddr = Output(UInt(32.W))
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
    val in = IO(Flipped(Decoupled(new MemPortBundle)))
    val out = IO(Decoupled(new Bundle {
        val rdata = Output(UInt(32.W))
    }))
    
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

    out.bits.rdata := 0.U

    in.ready := true.B
    val load_s1 = RegNext(in.bits.valid,false.B)
    out.valid := load_s1
    
    when(in.bits.valid){
        raddr := in.bits.raddr & ~3.U(32.W)
        shifted_rdata := rdata >> (in.bits.raddr(1,0) << 3)
        shifted_wdata := in.bits.wdata << (in.bits.waddr(1,0) << 3)
        switch(in.bits.mem_ctl){
            is(MEM_CTL.Read4B){
                out.bits.rdata := rdata
            }
            is(MEM_CTL.Read1B){
                out.bits.rdata := Cat(Fill(24, shifted_rdata(7)), shifted_rdata(7, 0))
            }
            is(MEM_CTL.Read1BU){
                out.bits.rdata := Cat(Fill(24,0.U),shifted_rdata(7, 0))
            }
            is(MEM_CTL.Read2B){
                out.bits.rdata := Cat(Fill(16, shifted_rdata(15)), shifted_rdata(15, 0))
            }
            is(MEM_CTL.Read2BU){
                out.bits.rdata := Cat(Fill(16, 0.U), shifted_rdata(15, 0))
            }
            is(MEM_CTL.Write4B){
                mask := "b1111".U;
                wdata := in.bits.wdata;
                waddr := in.bits.waddr & ~3.U(32.W)
                wen := true.B
            }
            is(MEM_CTL.Write1B){
                mask := 1.U << in.bits.waddr(1,0)
                wdata := shifted_wdata
                waddr := in.bits.waddr & ~3.U(32.W)
                wen := true.B
            }
            is(MEM_CTL.Write2B){
                mask := 3.U << in.bits.waddr(1,0)
                wdata := shifted_wdata
                waddr := in.bits.waddr & ~3.U(32.W)
                wen := true.B
            }
        }
    }


}