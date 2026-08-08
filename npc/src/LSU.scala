package npc

import chisel3._
import chisel3.util._
import npc.AXI.AXI4Bundle
import npc.PMU._
import os.read

class mtrace extends ExtModule{
    val io = FlatIO(new Bundle {
        val is_write = Input(Bool())
        val addr = Input(UInt(32.W))
        val data = Input(UInt(32.W))
        val size = Input(UInt(4.W))
        val valid = Input(Bool())
    })
    addResource("/mtrace.v")
}

class LSUReadPort extends Bundle{
    val mem_ctl = Output(MemControl())
    val addr = Output(UInt(32.W))
}
class LSUWritePort extends Bundle{
    val mem_ctl = Output(MemControl())
    val wdata = Output(UInt(32.W))
    val addr = Output(UInt(32.W))
}

class ysyx_26030092_LSU extends Module{
    val read_port = IO(Flipped(Decoupled(new LSUReadPort)))
    val write_port = IO(Flipped(Decoupled(new LSUWritePort)))
    val read_resp = IO(Decoupled(new Bundle {
        val rdata = Output(UInt(32.W))
        val error = Output(Bool())
    }))
    // val write_resp = IO(Decoupled(new Bundle {
    //     val error = Output(Bool())
    // }))

    val AXI4_port = IO(new AXI4Bundle)

    val perf_events = IO(Output(Vec(PerfEvent.numEvents, Bool())))
    perf_events.foreach(_ := false.B)

    val lsu_read_reg = Reg(Flipped(new LSUReadPort))
    val lsu_write_reg = Reg(Flipped(new LSUWritePort))
    val lsu_read_resp_reg = Reg(new Bundle {
        val rdata = Output(UInt(32.W))
        val error = Output(Bool())
    })
    val lsu_write_resp_reg = Reg(new Bundle {
        val error = Output(Bool())
    })

    val rdata = WireDefault(0.U(32.W))
    val shifted_rdata = AXI4_port.rdata >> (lsu_read_reg.addr(1,0) << 3)
    val shifted_wdata = lsu_write_reg.wdata << (lsu_write_reg.addr(1,0) << 3)

    val wmask = WireDefault(0.U(4.W))
    dontTouch(wmask)
    dontTouch(rdata)

    val mtrace_u = Module(new mtrace)
    mtrace_u.io.valid := false.B
    List( mtrace_u.io.data, mtrace_u.io.is_write, mtrace_u.io.size).map(_ := 0.U)
    //mtrace_u.io.addr := lsu_in_reg.addr

    read_resp.bits.rdata := DontCare

    read_port.ready := false.B
    write_port.ready := false.B
    read_resp.valid := false.B
    read_resp.bits := lsu_read_resp_reg

    AXI4_port.initMaster()
    AXI4_port.araddr := lsu_read_reg.addr
    AXI4_port.awaddr := lsu_write_reg.addr
    AXI4_port.wdata := lsu_write_reg.wdata
    AXI4_port.wstrb := wmask

    val s_idle :: s_wait_mem_free :: s_wait_mem_resp :: s_wait_fetch :: Nil = Enum(4)
    val r_state = RegInit(s_idle)
    val w_state = RegInit(s_idle)

    val waddr_ready = RegInit(false.B)
    val wdata_ready = RegInit(false.B)

    when(r_state === s_wait_mem_free || r_state === s_wait_mem_resp){
        perf_events(PerfEvent.LSU_read_mem_latency) := true.B
    }
    when(w_state === s_wait_mem_free || w_state === s_wait_mem_resp){
        perf_events(PerfEvent.LSU_write_mem_latency) := true.B
    }
    // when(r_state === s_wait_mem_free){
    //     perf_events(PerfEvent.LSU_read_wait_free_cycles) := true.B
    // }
    // when(r_state === s_wait_mem_resp){
    //     perf_events(PerfEvent.LSU_read_wait_resp_cycles) := true.B
    // }
    // when(w_state === s_wait_mem_free){
    //     perf_events(PerfEvent.LSU_write_wait_free_cycles) := true.B
    // }
    // when(w_state === s_wait_mem_resp){
    //     perf_events(PerfEvent.LSU_write_wait_resp_cycles) := true.B
    // }

    switch(r_state){
        is(s_idle){
            read_port.ready := true.B
            when(read_port.fire){
                r_state := s_wait_mem_free
                lsu_read_reg := read_port.bits
            }
        }
        is(s_wait_mem_free){
            AXI4_port.arsize := MuxLookup(lsu_read_reg.mem_ctl, 2.U)(Seq(
                MemControl.lh -> 1.U,
                MemControl.lhu -> 1.U,
                MemControl.lb -> 0.U,
                MemControl.lbu -> 0.U))
            AXI4_port.arvalid := true.B
            when(AXI4_port.arready){
                r_state := s_wait_mem_resp
            }
        }
        is(s_wait_mem_resp){
            AXI4_port.rready := true.B                
            when(AXI4_port.rvalid){
                r_state := s_wait_fetch
                mtrace_u.io.valid := true.B
                mtrace_u.io.is_write := false.B
                lsu_read_resp_reg.rdata := rdata
                mtrace_u.io.data := rdata
                lsu_read_resp_reg.error := AXI4_port.rresp
                perf_events(PerfEvent.LSU_get_data) := true.B
            }

        }
        is(s_wait_fetch){
            read_resp.valid := true.B
            when(read_resp.fire){
                r_state := s_idle
            }
        }
    }

    switch(w_state){
        is(s_idle){
            write_port.ready := true.B
            when(write_port.fire){
                w_state := s_wait_mem_free
                lsu_write_reg := write_port.bits
            }
        }
        is(s_wait_mem_free){
            when(!waddr_ready){
                AXI4_port.awaddr := lsu_write_reg.addr
                AXI4_port.awsize := MuxLookup(lsu_write_reg.mem_ctl, 2.U)(Seq(
                    MemControl.sh -> 1.U,
                    MemControl.sb -> 0.U))
                AXI4_port.awvalid := true.B
                when(AXI4_port.awready){
                    waddr_ready := true.B
                }
            }
            when(!wdata_ready){
                AXI4_port.wdata := shifted_wdata
                AXI4_port.wstrb := wmask
                AXI4_port.wlast := true.B
                AXI4_port.wvalid := true.B
                when(AXI4_port.wready){
                    wdata_ready := true.B
                }
            }
            when(waddr_ready && wdata_ready){
                w_state := s_wait_mem_resp
                waddr_ready := false.B
                wdata_ready := false.B
            }
        }
        is(s_wait_mem_resp){
            AXI4_port.bready := true.B
            when(AXI4_port.bvalid){
                w_state := s_wait_fetch
                mtrace_u.io.valid := true.B
                mtrace_u.io.is_write := true.B
                mtrace_u.io.data := lsu_write_reg.wdata
                lsu_write_resp_reg.error := AXI4_port.bresp
                perf_events(PerfEvent.LSU_get_data) := true.B
            }
        }
        // is(s_wait_fetch){
        //     write_resp.valid := true.B
        //     when(write_resp.fire){
        //         w_state := s_idle
        //     }
        // }
    }


    switch(lsu_read_reg.mem_ctl){
        is(MemControl.lw){
            rdata := AXI4_port.rdata
            mtrace_u.io.size := 4.U;
        }
        is(MemControl.lb){
            rdata := Cat(Fill(24, shifted_rdata(7)), shifted_rdata(7, 0))
            mtrace_u.io.size := 1.U;
        }
        is(MemControl.lbu){
            rdata := Cat(Fill(24,0.U),shifted_rdata(7, 0))
            mtrace_u.io.size := 1.U;
        }
        is(MemControl.lh){
            rdata := Cat(Fill(16, shifted_rdata(15)), shifted_rdata(15, 0))
            mtrace_u.io.size := 2.U;
        }
        is(MemControl.lhu){
            rdata := Cat(Fill(16, 0.U), shifted_rdata(15, 0))
            mtrace_u.io.size := 2.U;
        }
    }
    switch(lsu_write_reg.mem_ctl){
        is(MemControl.sw){
            wmask := "b1111".U;
            mtrace_u.io.size := 4.U;
            // pmem_unit.port.wdata := lsu_in_reg.wdata
        }
        is(MemControl.sb){
            mtrace_u.io.size := 1.U;
            wmask := 1.U << lsu_write_reg.addr(1,0)
            // pmem_unit.port.wdata := shifted_wdata
        }
        is(MemControl.sh){
            mtrace_u.io.size := 2.U;
            wmask := 3.U << lsu_write_reg.addr(1,0)
            // pmem_unit.port.wdata := shifted_wdata
        }
    }
}