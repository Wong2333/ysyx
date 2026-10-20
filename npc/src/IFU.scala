package npc

import chisel3._
import chisel3.util._
import npc.AXI.AXI4Bundle
import npc.PMU._
import npc.icache.icacheMainPipe
import npc.BPU.BPUPredictBundle

class PCBlackBox extends ExtModule{
    val io = FlatIO(new Bundle {
        val clock = Input(Clock())
        val reset = Input(Reset())
        val pc_next = Input(UInt(32.W))
        val pc_we = Input(Bool())
        val pc_4 = Input(Bool())
        val pc = Output(UInt(32.W))
    })
    addResource("/PCBlackBox.v")
}

class ysyx_26030092_IFU extends Module{

    val out = IO(Decoupled(new Bundle {
        val inst = UInt(32.W)
        val pc = UInt(32.W)
        val excp_code = UInt(32.W)
        val is_excp = Bool()
    }))

    val RedirectPC = IO(Input(UInt(32.W)))
    val flush_icache = IO(Input(Bool()))
    val flush = IO(Input(Bool()))

    val bpu_pc = IO(Output(UInt(30.W)))

    val bpu_predict = IO(Input(new BPUPredictBundle))

    val AXI4_port = IO(new AXI4Bundle)

    // PC 寄存器（架构 PC = 取指 PC，串行流水线下两者合一）
    val PC_reg = Module(new PCBlackBox())
    PC_reg.io.clock := clock
    PC_reg.io.reset := reset
    PC_reg.io.pc_next := bpu_predict.next_pc
    PC_reg.io.pc_we := false.B        // 在下面取指握手处统一赋值（见 req.fire 门控）
    PC_reg.io.pc_4 := false.B
    bpu_pc := PC_reg.io.pc(31,2)

    val inst_reg = RegInit(0.U(32.W))
    val error_reg = RegInit(false.B)

    out.valid := false.B


    val icache_main_pipe = Module(new icacheMainPipe(16,8))
    icache_main_pipe.AXI4_port <> AXI4_port
    icache_main_pipe.req.valid := false.B
    icache_main_pipe.req.addr := DontCare
    icache_main_pipe.icache_flush := flush_icache
    icache_main_pipe.resp.ready := false.B
    icache_main_pipe.flush := flush
    out.bits.pc := icache_main_pipe.resp.raddr
    out.bits.inst := icache_main_pipe.resp.rdata
    out.bits.excp_code := icache_main_pipe.resp.excp_code
    out.bits.is_excp := icache_main_pipe.resp.excp_trap

    icache_main_pipe.req.addr := PC_reg.io.pc
    icache_main_pipe.req.valid := ~flush
    when(icache_main_pipe.req.ready & icache_main_pipe.req.valid){
        PC_reg.io.pc_4 := true.B
    }

    PC_reg.io.pc_we := bpu_predict.valid && bpu_predict.take &&
                       icache_main_pipe.req.ready && icache_main_pipe.req.valid
    when(flush){                       // flush 优先级最高，放最后覆盖
        PC_reg.io.pc_we := true.B
        PC_reg.io.pc_next := RedirectPC
    }
    out.valid := ~flush & icache_main_pipe.resp.valid
    icache_main_pipe.resp.ready := out.ready


    val ebreak = Module(new EbreakBlackBox())
    ebreak.io.ebreak_trap := false.B
    when(AXI4_port.rvalid && AXI4_port.rready && AXI4_port.rresp =/= 0.U){
        ebreak.io.ebreak_trap := true.B
    }

    PerfCounter("IFU_get_inst_cycles") := ~flush
    PerfCounter("IFU_wait_icache")  := icache_main_pipe.req.valid && ~icache_main_pipe.req.ready
    PerfCounter("IFU_wait_miss")    := icache_main_pipe.req.valid && ~icache_main_pipe.req.ready && icache_main_pipe.refilling
    PerfCounter("IFU_wait_backend") := icache_main_pipe.req.valid && ~icache_main_pipe.req.ready && ~icache_main_pipe.refilling
    PerfCounter("IFU_wait_resp")    := icache_main_pipe.req.ready && ~icache_main_pipe.resp.valid && ~flush
    PerfCounter("IFU_wait_idu")     := out.valid && ~out.fire
    PerfCounter("IFU_fetch")        := out.fire
}
