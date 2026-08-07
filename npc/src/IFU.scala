package npc

import chisel3._
import chisel3.util._
import npc.AXI.AXI4Bundle
import npc.PMU._

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

    // WBU 的 commit 事件：valid 时更新 PC（跳转/trap）并开始下一轮取指
    val PCUpdatePort = IO(Input(new PCUpdatePortBundle))
    // trap 跳转目标（mtvec 存储在 RFS，IFU 直接读）
    val mtvec = IO(Input(UInt(32.W)))

    val perf_events = IO(Output(Vec(PerfEvent.numEvents, Bool())))
    perf_events.foreach(_ := false.B)

    val AXI4_port = IO(new AXI4Bundle)

    // PC 寄存器（架构 PC = 取指 PC，串行流水线下两者合一）
    val PC_reg = Module(new PCBlackBox())
    PC_reg.io.clock := clock
    PC_reg.io.reset := reset
    PC_reg.io.pc_next := DontCare
    PC_reg.io.pc_we := false.B
    PC_reg.io.pc_4 := false.B
    when(PCUpdatePort.valid){
        PC_reg.io.pc_we := PCUpdatePort.pc_we
        PC_reg.io.pc_next := Mux(PCUpdatePort.is_trap, mtvec, PCUpdatePort.pc_next)
        PC_reg.io.pc_4 := !PCUpdatePort.pc_we
    }

    val inst_reg = RegInit(0.U(32.W))
    val error_reg = RegInit(false.B)

    out.valid := false.B

    AXI4_port.initMaster()
    AXI4_port.araddr := PC_reg.io.pc

    out.bits.pc := PC_reg.io.pc
    out.bits.inst := inst_reg

    val s_idle :: s_wait_mem_free :: s_wait_mem_data :: s_wait_fetch :: Nil = Enum(4)
    // 复位后 PC=CONFIG_START_ADDR，直接取第一条指令
    val status = RegInit(s_wait_mem_free)

    when(status === s_wait_mem_free || status === s_wait_mem_data){
        perf_events(PerfEvent.IFU_wait_mem_cycles) := true.B
    }

    switch(status){
        is(s_idle){
            // 等待 WBU 写回：commit 时 PC 已更新，开始取下一条
            when(PCUpdatePort.valid){
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
                perf_events(PerfEvent.IFU_fetch_instruction) := true.B
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

}
