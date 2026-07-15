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

    val mem_port = IO(Flipped(new MemPort))

    // val pmem_read_inst = Module(new MemUnit())

    val pc_reg = RegInit(0.U(32.W))
    val inst_reg = RegInit(0.U(32.W))
    val error_reg = RegInit(false.B)
    
    in.ready := false.B
    out.valid := false.B

    mem_port.AR.arvalid := false.B
    mem_port.AR.araddr := pc_reg
    mem_port.R.rready := false.B
    mem_port.AW.awaddr := 0.U
    mem_port.AW.awvalid := false.B
    mem_port.W.wdata := 0.U
    mem_port.W.wstrb := 0.U
    mem_port.W.wvalid := false.B
    mem_port.B.bready := false.B

    out.bits.pc := pc_reg
    out.bits.inst := inst_reg

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
            mem_port.AR.arvalid := true.B
            when(mem_port.AR.arready){
                status := s_wait_mem_data
            }
        }
        is(s_wait_mem_data){
            mem_port.R.rready := true.B
            when(mem_port.R.rvalid){
                inst_reg := mem_port.R.rdata
                error_reg := mem_port.R.rresp
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

    val ebreak = Module(new EbreakBlackBox())
    ebreak.io.ebreak_trap := false.B
    when(mem_port.R.rvalid && mem_port.R.rready && mem_port.R.rresp){
        ebreak.io.ebreak_trap := true.B
    }
}