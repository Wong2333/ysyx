package npc

import chisel3._

class DifftestReset extends ExtModule{
    val io = FlatIO(new Bundle {
        val reset = Input(Bool())
    })
    addResource("/DifftestReset.v")
}

class DifftestCommit extends ExtModule{
  val io = FlatIO(new Bundle{
    val commit = Input(Bool());
  })
  addResource("/DifftestCommit.v")
}

class Difftest extends Module{
    val commit = IO(Input(Bool()))
    val reset_reg = RegNext(reset.asBool,false.B)
    val difftest_reset = Module(new DifftestReset)
    difftest_reset.io.reset := false.B

    when(reset_reg && !reset.asBool){
        difftest_reset.io.reset := true.B
    }

    val inst_commit = Module(new DifftestCommit)
    inst_commit.io.commit := commit
}