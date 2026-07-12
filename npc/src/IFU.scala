package npc

import chisel3._
import chisel3.util._


class IFU extends Module{

    val in = IO(Flipped(Decoupled(new Bundle {
        val pc = UInt(32.W)
    })))
    val out = IO(Decoupled(new Bundle {
        val inst = UInt(32.W)
        val pc = UInt(32.W)
    }))

    val pmem_read_inst = Module(new MemUnit())

    val pc_reg = RegInit(0.U(32.W))
    val inst_reg = RegInit(0.U(32.W))
    val error = Wire(Bool())
    
    in.ready := false.B
    out.valid := false.B

    
    pmem_read_inst.port.addr := pc_reg
    pmem_read_inst.port.wen := false.B
    pmem_read_inst.port.wdata := DontCare
    pmem_read_inst.port.wmask := DontCare
    pmem_read_inst.port.reqValid := false.B
    pmem_read_inst.port.respReady := false.B

    out.bits.pc := pc_reg
    out.bits.inst := inst_reg
    error := pmem_read_inst.port.error

    val s_idle :: s_wait_mem_free :: s_wait_mem_data :: s_wait_fetch :: Nil = Enum(4)
    val status = RegInit(s_idle)

    switch(status){
        is(s_idle){
            in.ready := true.B
            when(in.fire){
                pc_reg := in.bits.pc
                status := s_wait_mem_free
            }
        }
        is(s_wait_mem_free){
            pmem_read_inst.port.reqValid := true.B
            when(pmem_read_inst.port.reqReady){
                status := s_wait_mem_data
            }
        }
        is(s_wait_mem_data){
            pmem_read_inst.port.respReady := true.B
            when(pmem_read_inst.port.respValid){
                inst_reg := pmem_read_inst.port.rdata
                status := s_wait_fetch
            }
        }
        is(s_wait_fetch){
            out.valid := true.B
            when(out.ready){
                status := s_idle
            }
        }
    }
}