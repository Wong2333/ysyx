package npc

import chisel3._
import chisel3.util._
import npc.AXI.AXI4Bundle
import npc.PMU._
import npc.icache.icacheMainPipe

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
    }))

    val RedirectPC = IO(Input(UInt(32.W)))
    val flush_icache = IO(Input(Bool()))
    val flush = IO(Input(Bool()))

    val AXI4_port = IO(new AXI4Bundle)

    // PC 寄存器（架构 PC = 取指 PC，串行流水线下两者合一）
    val PC_reg = Module(new PCBlackBox())
    PC_reg.io.clock := clock
    PC_reg.io.reset := reset
    PC_reg.io.pc_next := DontCare
    PC_reg.io.pc_we := false.B
    PC_reg.io.pc_4 := false.B
    when(flush){
        PC_reg.io.pc_we := true.B
        PC_reg.io.pc_next := RedirectPC
    }

    val inst_reg = RegInit(0.U(32.W))
    val error_reg = RegInit(false.B)

    out.valid := false.B

    val s_req_inst :: s_wait_resp :: s_wait_fetch :: Nil = Enum(3)
    // 复位后 PC=CONFIG_START_ADDR，直接取第一条指令
    val state = RegInit(s_req_inst)

    PerfCounter("IFU_get_inst_cycles") := (state === s_req_inst || state === s_wait_resp)
    // 取指停顿分项：被 icache 反压（refill/槽满） / 等响应（命中延迟+miss 惩罚） / 被 IDU 反压
    PerfCounter("IFU_wait_icache") := (state === s_req_inst) && ~icache_main_pipe.req.ready
    PerfCounter("IFU_wait_resp")   := (state === s_wait_resp) && ~icache_main_pipe.resp.valid
    PerfCounter("IFU_wait_idu")    := (state === s_wait_resp) && icache_main_pipe.resp.valid && ~out.fire
    PerfCounter("IFU_fetch")       := out.fire

    val icache_main_pipe = Module(new icacheMainPipe(16,16))
    icache_main_pipe.AXI4_port <> AXI4_port
    icache_main_pipe.req.valid := false.B
    icache_main_pipe.req.addr := DontCare
    icache_main_pipe.icache_flush := flush_icache
    icache_main_pipe.resp.ready := false.B
    icache_main_pipe.flush := flush
    out.bits.pc := icache_main_pipe.resp.raddr
    out.bits.inst := icache_main_pipe.resp.rdata

    switch(state){
        is(s_req_inst){
            icache_main_pipe.req.addr := PC_reg.io.pc
            icache_main_pipe.req.valid := ~flush
            when(flush){ state := s_req_inst}
            .elsewhen(icache_main_pipe.req.ready & icache_main_pipe.req.valid){ 
                state := s_wait_resp
                PC_reg.io.pc_4 := true.B
            }
        }
        is(s_wait_resp){
            out.valid := ~flush & icache_main_pipe.resp.valid
            icache_main_pipe.resp.ready := out.ready
            when(flush) {state := s_req_inst }
            .elsewhen(out.fire){
                state := s_req_inst

            }
            
        }
    }

    val ebreak = Module(new EbreakBlackBox())
    ebreak.io.ebreak_trap := false.B
    when(AXI4_port.rvalid && AXI4_port.rready && AXI4_port.rresp =/= 0.U){
        ebreak.io.ebreak_trap := true.B
    }

}
