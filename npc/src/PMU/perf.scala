package npc.PMU

import chisel3._

// 性能事件索引：每个事件对应 PerfCounterArray 里的一根事件线和计数器
object PerfEvent {
  val IFU_fetch_instruction = 0
  val LSU_get_data = 1
  val LSU_access_mem_latency = 2
  val EXU_finished = 3
  val IDU_jump_instruction = 4
  val IDU_brench_instruction = 5
  val IDU_load_instruction = 6
  val IDU_calculate_instruction = 7
  val IDU_csr_instruction = 8
  val IDU_store_instruction = 9
  val IFU_get_inst_cycles = 10
  val LSU_wait_free_cycles = 11
  val LSU_wait_resp_cycles = 12
  val PIPE_idle_cycles = 13
  val WBU_commit_instruction = 14
  val numEvents = 15
}

// 计数器阵列：每个事件一根 valid 线，独立 64 位计数器并行累加（同一周期可多事件同时发生）。
// 仿真结束时通过 DPI-C export 一次性读出打印。
class PerfCounterArray extends ExtModule {
  val io = IO(new Bundle {
    val clock = Input(Clock())
    val reset = Input(Bool())
    val events = Input(UInt(PerfEvent.numEvents.W))
  })
  addResource("/perf.v")
}
