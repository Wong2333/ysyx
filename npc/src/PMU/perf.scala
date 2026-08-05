package npc.PMU

import chisel3._

object PerfName extends ChiselEnum{
    val IFU_fetch_instruction,
    LSU_get_data,
    EXU_finished,
    IDU_load_instruction = Value
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

        def inc(targetId :PerfName, delta : UInt) = {
            valid := true.B 
            id := targetId 
            cnt := delta         
        }
    })
    addResource("/perf.v")
}