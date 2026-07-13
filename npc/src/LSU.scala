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

class LSUBundle extends Bundle{
    val mem_ctl = Output(MemControl())
    val wdata = Output(UInt(32.W))
    val addr = Output(UInt(32.W))
}

class LSU extends Module{
    val in = IO(Flipped(Decoupled(new LSUBundle)))
    val out = IO(Decoupled(new Bundle {
        val rdata = Output(UInt(32.W))
        val error = Output(Bool())
    }))
    val pmem_unit = Module(new MemUnit())
    
    val lsu_in_reg = Reg(Flipped(new LSUBundle))
    val lsu_out_reg = Reg(new Bundle {
        val rdata = Output(UInt(32.W))
        val error = Output(Bool())
    })

    val rdata = WireDefault(0.U(32.W))
    val shifted_rdata = pmem_unit.port.rdata >> (lsu_in_reg.addr(1,0) << 3)
    val shifted_wdata = lsu_in_reg.wdata << (lsu_in_reg.addr(1,0) << 3)
    val is_write = lsu_in_reg.mem_ctl.isOneOf(MemControl.sw, MemControl.sh, MemControl.sb)
    val wmask = WireDefault(0.U(4.W))
    dontTouch(wmask)
    dontTouch(rdata)
    
    pmem_unit.port.addr := lsu_in_reg.addr
    pmem_unit.port.wdata := shifted_wdata
    pmem_unit.port.wmask := DontCare
    pmem_unit.port.wen := false.B
    pmem_unit.port.reqValid := false.B
    pmem_unit.port.respReady := false.B

    out.bits.rdata := DontCare

    in.ready := false.B
    out.valid := false.B
    out.bits := lsu_out_reg

    val s_idle :: s_wait_mem_free :: s_wait_mem_resp :: s_wait_fetch :: Nil = Enum(4)
    val status = RegInit(s_idle)

    switch(status){
        is(s_idle){
            in.ready := true.B
            when(in.fire){
                status := s_wait_mem_free
                lsu_in_reg := in.bits
            }
        }
        is(s_wait_mem_free){
            pmem_unit.port.reqValid := true.B
            pmem_unit.port.wmask := wmask
            pmem_unit.port.wen := is_write
            when(pmem_unit.port.reqReady){
                status := s_wait_mem_resp
            }
        }
        is(s_wait_mem_resp){
            pmem_unit.port.respReady := true.B
            when(pmem_unit.port.respValid){
                when(!is_write){
                    lsu_out_reg.rdata := rdata
                    lsu_out_reg.error := pmem_unit.port.error
                }
                status := s_wait_fetch
            }
        }
        is(s_wait_fetch){
            out.valid := true.B
            when(out.fire){
                status := s_idle
            }
        }
    }


    switch(lsu_in_reg.mem_ctl){
        is(MemControl.lw){
            rdata := pmem_unit.port.rdata
        }
        is(MemControl.lb){
            rdata := Cat(Fill(24, shifted_rdata(7)), shifted_rdata(7, 0))
        }
        is(MemControl.lbu){
            rdata := Cat(Fill(24,0.U),shifted_rdata(7, 0))
        }
        is(MemControl.lh){
            rdata := Cat(Fill(16, shifted_rdata(15)), shifted_rdata(15, 0))
        }
        is(MemControl.lhu){
            rdata := Cat(Fill(16, 0.U), shifted_rdata(15, 0))
        }
        is(MemControl.sw){
            wmask := "b1111".U;
            // pmem_unit.port.wdata := lsu_in_reg.wdata
        }
        is(MemControl.sb){
            wmask := 1.U << lsu_in_reg.addr(1,0)
            // pmem_unit.port.wdata := shifted_wdata
        }
        is(MemControl.sh){
            wmask := 3.U << lsu_in_reg.addr(1,0)
            // pmem_unit.port.wdata := shifted_wdata
        }
    }
}