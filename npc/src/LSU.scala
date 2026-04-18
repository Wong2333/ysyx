package npc

import chisel3._
import chisel3.util._
import npc.PmemReadBlackBox


class PmemWriteBlackBox extends ExtModule {
    val io = FlatIO(new Bundle {
        val clock = Input(Clock())
        val waddr = Input(UInt(32.W))
        val wdata = Input(UInt(32.W))
        val wmask = Input(UInt(32.W))
        val we = Input(Bool())
    })
    addResource("/PmemWriteBlackBox.v")
}
class LSU extends Module{
    val io = IO(new Bundle {
        val valid = Input(Bool())
        val wen = Input(Bool())
        val mem_ctl = Input(UInt(4.W))
        val src1 = Input(UInt(32.W))
        val src2 = Input(UInt(32.W))
        val ALU_out = Input(UInt(32.W))
        val mem_out = Output(UInt(32.W))
    })

    val raddr = WireDefault(0.U(32.W))
    val waddr = WireDefault(0.U(32.W))
    val mask = WireDefault(0.U(4.W))
    val rdata = WireDefault(0.U(32.W))
    val wdata = WireDefault(0.U(32.W))

    val shifted_rdata = rdata >> (raddr(1,0) << 3)
    val shifted_wdata = io.src2 << (waddr(1,0) << 3)

    val pmem_read = Module(new PmemReadBlackBox())
    pmem_read.io.raddr := 0.U

    val write_en = WireDefault(false.B)
    val pmem_write = Module(new PmemWriteBlackBox())
    pmem_write.io.waddr := waddr
    pmem_write.io.wdata := wdata
    pmem_write.io.wmask := mask
    pmem_write.io.we := write_en

    when(io.valid){
        raddr := io.ALU_out
        waddr := io.ALU_out
        when(io.wen){
            rdata := 0.U
            write_en := true.B
        }.otherwise{
            pmem_read.io.raddr := raddr
            rdata := pmem_read.io.rdata
            write_en := false.B
        }
    }
    io.mem_out := 0.U
    switch(io.mem_ctl){
        is(1.U){
            io.mem_out := rdata
        }
        is(2.U){
            io.mem_out := Cat(Fill(24, shifted_rdata(7)), shifted_rdata(7, 0))
        }
        is(3.U){
            io.mem_out := Cat(Fill(24,0.U),shifted_rdata(7, 0))
        }
        is(4.U){
            mask := "b1111".U;
            wdata := io.src2;
        }
        is(5.U){
            mask := 1.U << waddr(1,0)
            io.mem_out := shifted_wdata
        }
    }


}