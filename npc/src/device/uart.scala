package npc.device

import chisel3._
import npc.MemPort

class SkipDifftest extends ExtModule{
    val io = FlatIO(new Bundle{
        val skip = Input(Bool())
    })
    addResource("/SkipDifftest.v")
}

class uart extends Module{
    val port = IO(new MemPort)
    // val skip_difftest = IO(Output(Bool()))
    // skip_difftest := false.B

    port.AR.arready := false.B
    port.AW.awready := true.B
    port.B.bvalid := true.B
    port.B.bresp := false.B
    port.R.rdata := 0.U
    port.R.rresp := false.B
    port.R.rvalid := false.B
    port.W.wready := true.B

    when(port.AW.awvalid){
        assert(port.AW.awaddr === 0x10000000.U)
    }
    when(port.W.wvalid){
        skip_difftest := true.B
        printf("%c",port.W.wdata)
    }
}