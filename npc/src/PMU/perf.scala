package npc.PMU

import chisel3._

object PerfName extends ChiselEnum{
    val IFU_fetch_instruction,
    LSU_get_data,
    EXU_finished,
    IDU_load_instruction,
    none = Value(0x0fffffff.U(32.W))
}

class PerfCounter extends ExtModule{
    val io = FlatIO(new Bundle{
        val valid = Input(Bool())
        val id = Input(PerfName())
        val cnt = Input(UInt(32.W))
    })
    addResource("/perf.v")
}