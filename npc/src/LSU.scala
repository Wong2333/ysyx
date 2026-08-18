package npc

import chisel3._
import chisel3.util._
import npc.AXI.AXI4Bundle
import npc.PMU._

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

class LSUBundle extends Bundle{
    val mem_op     = Output(MemOp())      // none / load / store
    val mem_size   = Output(MemSize())    // byte / half / word
    val mem_signed = Output(Bool())       // 符号扩展（仅 load 有效）
    val wdata = Output(UInt(32.W))
    val addr  = Output(UInt(32.W))
}

class ysyx_26030092_LSU extends Module{
    val in = IO(Flipped(Decoupled(new LSUBundle)))
    val out = IO(Decoupled(new Bundle {
        val rdata = Output(UInt(32.W))
        val error = Output(Bool())
    }))

    val AXI4_port = IO(new AXI4Bundle)

    val lsu_in_reg = Reg(Flipped(new LSUBundle))
    val lsu_out_reg = Reg(new Bundle {
        val rdata = Output(UInt(32.W))
        val error = Output(Bool())
    })

    val rdata = WireDefault(0.U(32.W))
    val shifted_rdata = AXI4_port.rdata >> (lsu_in_reg.addr(1,0) << 3)
    val shifted_wdata = lsu_in_reg.wdata << (lsu_in_reg.addr(1,0) << 3)
    val is_write = lsu_in_reg.mem_op === MemOp.store
    val wmask = WireDefault(0.U(4.W))
    dontTouch(wmask)
    dontTouch(rdata)

    val awsize = MuxLookup(lsu_in_reg.mem_size, 2.U)(Seq(
        MemSize.byte -> 0.U,
        MemSize.half -> 1.U))
    val mtrace_size = MuxLookup(lsu_in_reg.mem_size, 4.U)(Seq(
        MemSize.byte -> 1.U,
        MemSize.half -> 2.U))
    switch(lsu_in_reg.mem_size){
        is(MemSize.byte){ wmask := 1.U << lsu_in_reg.addr(1,0) }
        is(MemSize.half){ wmask := 3.U << lsu_in_reg.addr(1,0) }
        is(MemSize.word){ wmask := "b1111".U }
    }
    // load 数据扩展（符号/零扩展由 mem_signed 决定）
    switch(lsu_in_reg.mem_size){
        is(MemSize.byte){ rdata := Mux(lsu_in_reg.mem_signed,
            Cat(Fill(24, shifted_rdata(7)), shifted_rdata(7,0)),
            Cat(Fill(24, 0.U), shifted_rdata(7,0))) }
        is(MemSize.half){ rdata := Mux(lsu_in_reg.mem_signed,
            Cat(Fill(16, shifted_rdata(15)), shifted_rdata(15,0)),
            Cat(Fill(16, 0.U), shifted_rdata(15,0))) }
        is(MemSize.word){ rdata := shifted_rdata }
    }

    val mtrace_u = Module(new mtrace)
    mtrace_u.io.valid := false.B
    List( mtrace_u.io.data, mtrace_u.io.is_write, mtrace_u.io.size).map(_ := 0.U)
    mtrace_u.io.addr := lsu_in_reg.addr

    out.bits.rdata := DontCare

    in.ready := false.B
    out.valid := false.B
    out.bits := lsu_out_reg

    AXI4_port.initMaster()
    AXI4_port.araddr := lsu_in_reg.addr
    AXI4_port.awaddr := lsu_in_reg.addr
    AXI4_port.wdata := lsu_in_reg.wdata
    AXI4_port.wstrb := wmask

    val s_idle :: s_wait_mem_free :: s_wait_mem_resp :: s_wait_fetch :: Nil = Enum(4)
    val state = RegInit(s_idle)

    val waddr_ready = RegInit(false.B)
    val wdata_ready = RegInit(false.B)

    PerfCounter("LSU_access_latency") := (state === s_wait_mem_free || state === s_wait_mem_resp)
    PerfCounter("LSU_wait_free") := (state === s_wait_mem_free)
    PerfCounter("LSU_wait_resp") := (state === s_wait_mem_resp)

    switch(state){
        is(s_idle){
            in.ready := true.B
            when(in.fire){
                state := s_wait_mem_free
                lsu_in_reg := in.bits
            }
        }
        is(s_wait_mem_free){
            when(is_write){
                when(!waddr_ready){
                    AXI4_port.awaddr := lsu_in_reg.addr
                    AXI4_port.awsize := awsize
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
                    state := s_wait_mem_resp
                    waddr_ready := false.B
                    wdata_ready := false.B
                }

            }.otherwise{
                AXI4_port.arsize := awsize
                AXI4_port.arvalid := true.B
                when(AXI4_port.arready){
                    state := s_wait_mem_resp
                }

            }
        }
        is(s_wait_mem_resp){
            when(is_write){
                AXI4_port.bready := true.B
                when(AXI4_port.bvalid){                    
                    state := s_wait_fetch
                    mtrace_u.io.valid := true.B
                    mtrace_u.io.is_write := true.B
                    mtrace_u.io.size := mtrace_size
                    mtrace_u.io.data := lsu_in_reg.wdata
                    lsu_out_reg.error := AXI4_port.bresp
                    
                }
            }.otherwise{
                AXI4_port.rready := true.B                
                when(AXI4_port.rvalid){
                    state := s_wait_fetch
                    mtrace_u.io.valid := true.B
                    mtrace_u.io.is_write := false.B
                    mtrace_u.io.size := mtrace_size
                    lsu_out_reg.rdata := rdata
                    mtrace_u.io.data := rdata
                    lsu_out_reg.error := AXI4_port.rresp
                }

            }
        }
        is(s_wait_fetch){
            out.valid := true.B
            when(out.fire){
                state := s_idle
            }
        }
    }

    PerfCounter("LSU_get_data") := (is_write && AXI4_port.bvalid && AXI4_port.bready) ||
                                   (!is_write && AXI4_port.rvalid && AXI4_port.rready)
}