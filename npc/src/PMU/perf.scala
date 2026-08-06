package npc.PMU

import chisel3._

object PerfName extends ChiselEnum{
    val IFU_fetch_instruction,
    LSU_get_data,
    LSU_access_mem_latency,
    EXU_finished,
    IDU_jump_instruction,
    IDU_brench_instruction,
    IDU_load_instruction,
    IDU_calculate_instruction,
    IDU_csr_instruction,
    IDU_store_instruction,
    IFU_wait_mem_cycles,
    LSU_wait_free_cycles,
    LSU_wait_resp_cycles,
    PIPE_idle_cycles,
    WBU_commit_instruction = Value
    val none = Value(0x0fffffff.U(32.W))
}

class PerfCounter extends ExtModule{
    val io = FlatIO(new Bundle{
        val valid = Input(Bool())
        val id = Input(PerfName())
        val cnt = Input(UInt(32.W))

        def init() = {
            valid := false.B
            id := PerfName.none
            cnt := 0.U
        }

        def inc(targetId :PerfName.Type, delta : UInt) = {
            valid := true.B 
            id := targetId 
            cnt := delta         
        }
    })
    addResource("/perf.v")
}