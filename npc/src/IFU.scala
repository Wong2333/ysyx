package npc

import chisel3._
import chisel3.util._
import npc.AXI.AXI4Bundle
import npc.PMU._
import npc.BPU.BPUPredictBundle
import npc.icache.ysyx_26030092_icacheMainPipe_reg

class instEntry extends Bundle{
    val inst = UInt(32.W)
    val pc = UInt(32.W)
    val excp_code = UInt(32.W)
    val is_excp = Bool()
}

class ysyx_26030092_PCBlackBox extends ExtModule{
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

    val out = IO(Decoupled(new instEntry))

    val RedirectPC = IO(Input(UInt(32.W)))
    val flush_icache = IO(Input(Bool()))
    val flush = IO(Input(Bool()))

    val bpu_pc = IO(Output(UInt(30.W)))

    val bpu_predict = IO(Input(new BPUPredictBundle))

    val AXI4_port = IO(new AXI4Bundle)

    val PC_reg = Module(new ysyx_26030092_PCBlackBox())
    PC_reg.io.clock := clock
    PC_reg.io.reset := reset

    val inst_queue = Module(new Queue(new instEntry, 2, hasFlush = true))
    inst_queue.io.flush.get := flush
    
    val icache_main_pipe = Module(new ysyx_26030092_icacheMainPipe_reg(16,8))
    icache_main_pipe.AXI4_port <> AXI4_port
    icache_main_pipe.icache_flush := flush_icache
    icache_main_pipe.req.valid := ~flush
    icache_main_pipe.req.addr := PC_reg.io.pc
    icache_main_pipe.flush := flush

    inst_queue.io.enq <> icache_main_pipe.resp

    out <> inst_queue.io.deq

    val icahce_main_pipe_fire = icache_main_pipe.req.ready & icache_main_pipe.req.valid
    PC_reg.io.pc_4 := icahce_main_pipe_fire // 握手后自增PC
    
    val bpu_taken = bpu_predict.valid && bpu_predict.take && icahce_main_pipe_fire
    PC_reg.io.pc_we := bpu_taken //分支预测
    PC_reg.io.pc_next := bpu_predict.next_pc
    bpu_pc := PC_reg.io.pc(31,2)

    when(flush){                       // flush 优先级最高，放最后覆盖
        PC_reg.io.pc_we := true.B
        PC_reg.io.pc_next := RedirectPC
        out.valid := false.B
    }

    PerfCounter("IFU_get_inst_cycles") := ~flush
    PerfCounter("IFU_wait_resp")    := icache_main_pipe.req.ready && ~icache_main_pipe.resp.valid && ~flush
    PerfCounter("IFU_fetch")        := out.fire

    // 只用于仿真，检测访存错误
    val ebreak = Module(new ysyx_26030092_EbreakBlackBox())
    ebreak.io.ebreak_trap := false.B
    when(AXI4_port.rvalid && AXI4_port.rready && AXI4_port.rresp =/= 0.U){
        ebreak.io.ebreak_trap := true.B
    }
}
