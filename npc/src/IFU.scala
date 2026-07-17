package npc

import chisel3._
import chisel3.util._
import npc.AXI.AXI4Bundle

class trace extends ExtModule{
    val pc = FlatIO(Input(UInt(32.W)))
    val inst = FlatIO(Input(UInt(32.W)))
    val valid = FlatIO(Input(Bool()))
    addResource("/trace.v")
}

class ysyx_26030092_IFU extends Module{

    val in = IO(Flipped(Decoupled(new Bundle {
        val pc = UInt(32.W)
    })))
    val out = IO(Decoupled(new Bundle {
        val inst = UInt(32.W)
        val pc = UInt(32.W)
    }))

    val AXI4_port = IO(new AXI4Bundle)

    val pc_reg = RegInit(0.U(32.W))
    val inst_reg = RegInit(0.U(32.W))
    val error_reg = RegInit(false.B)
    
    in.ready := false.B
    out.valid := false.B

    AXI4_port.initMaster()
    AXI4_port.araddr := pc_reg

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
            AXI4_port.arvalid := true.B
            when(AXI4_port.arready){
                status := s_wait_mem_data
            }
        }
        is(s_wait_mem_data){
            AXI4_port.rready := true.B
            when(AXI4_port.rvalid){
                inst_reg := AXI4_port.rdata
                error_reg := AXI4_port.rresp
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
    when(AXI4_port.rvalid && AXI4_port.rready && AXI4_port.rresp =/= 0.U){
        ebreak.io.ebreak_trap := true.B
    }
    
    val trace_u = Module(new trace)
    trace_u.pc := 0.U
    trace_u.inst := 0.U
    trace_u.valid := false.B
    when(out.fire){
        trace_u.valid := true.B
        trace_u.pc := out.bits.pc
        trace_u.pc := out.bits.inst
    }
}