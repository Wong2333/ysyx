package npc.PMU

import chisel3._

// 性能事件索引：每个事件对应 PerfCounterArray 里的一根事件线和计数器
object PerfEvent {
  val IFU_fetch_instruction = 0
  val LSU_get_data = 1
  val LSU_read_mem_latency = 2
  val LSU_write_mem_latency = 3
  val EXU_finished = 4
  val IDU_jump_instruction = 5
  val IDU_brench_instruction = 6
  val IDU_load_instruction = 7
  val IDU_calculate_instruction = 8
  val IDU_csr_instruction = 9
  val IDU_store_instruction = 10
  val IFU_wait_mem_cycles = 11
  val LSU_wait_free_cycles = 12
  val LSU_wait_resp_cycles = 13
  val PIPE_idle_cycles = 14
  val WBU_commit_instruction = 15
  val numEvents = 16
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
