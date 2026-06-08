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

    val pmem_unit = Module(new MemUnit())

    val raddr = WireDefault(0.U(32.W))
    val rdata = WireDefault(0.U(32.W))
    pmem_unit.port.addr := raddr 
    rdata := pmem_unit.port.rdata

    pmem_unit.port.addr := waddr
    pmem_unit.port.wdata := wdata
    pmem_unit.port.wmask := mask
    pmem_unit.port.wen := wen

    out.bits.rdata := 0.U

    in.ready := false.B
    out.valid := false.B
    val s_idle :: s_buzy :: s_ready :: Nil = Enum(3)
    val status = RegInit(s_idle)

    switch(status){
        is(s_idle){
            in.ready := true.B
            when(in.fire){
                status := s_buzy
            }
        }
        is(s_buzy){
            in.ready := false.B
            status := s_ready
        }
        is(s_ready){
            out.valid := true.B
            when(out.fire){
                status := s_idle
                printf("rdata: %x\n",out.bits.rdata)
            }
        }
    }

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