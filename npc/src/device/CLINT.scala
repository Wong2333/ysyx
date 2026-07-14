package npc.device

import chisel3._
import chisel3.util._
import npc.MemPort

class CLINT extends Module{
    val port = IO(new MemPort)

    val skip_difftest = Module(new SkipDifftest)
    skip_difftest.io.skip := port.W.wvalid

    port.AR.arready := false.B
    port.AW.awready := false.B
    port.B.bvalid := false.B
    port.B.bresp := false.B
    port.R.rdata := 0.U
    port.R.rresp := false.B
    port.R.rvalid := false.B
    port.W.wready := false.B
    
    val mtime = RegInit(0.U(64.W))
    mtime := mtime + 1.U

    val out_reg = Reg(UInt(32.W))

    val s_idle :: s_busy :: Nil = Enum(2)
    val status = RegInit(s_idle)

    switch(status){
        is(s_idle){
            when(port.AW.awvalid){
                printf("reading\n")
                when(port.AW.awaddr === (0x10000000 + 48).U){
                    port.AW.awready := true.B
                    out_reg := mtime(31,0)
                    status := s_busy
                }
                when(port.AW.awaddr === (0x10000000 + 48 + 4).U){
                    port.AW.awready := true.B
                    out_reg := mtime(63,32)
                    status := s_busy
                }
            }
        }
        is(s_busy){
            port.R.rdata := out_reg
            port.R.rresp := false.B
            port.R.rvalid := true.B
            when(port.R.rready){
                printf("read mtime\n")
                skip_difftest.io.skip := true.B
                status := s_idle
            }
        }
    }
}