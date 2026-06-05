package npc

import chisel3._
import chisel3.util._

class PmemReadBlackBox extends ExtModule{
    val io = FlatIO(new Bundle{
        val clock = Input(Clock())
        val raddr = Input(UInt(32.W))
        val rdata = Output(UInt(32.W))
    })
    addResource("/PmemReadBlackBox.v")
}

class IFU extends Module{

    val in = IO(Flipped(Decoupled(new Bundle {
        val pc = UInt(32.W)
    })))
    val out = IO(Decoupled(new Bundle {
        val inst = UInt(32.W)
        val pc = UInt(32.W)
    }))

    val pmem_read_inst = Module(new PmemReadBlackBox)
    pmem_read_inst.io.clock := clock

    val inst = RegInit(0.U(32.W))

    in.ready := false.B
    pmem_read_inst.io.raddr := in.bits.pc
    out.bits.inst := inst
    out.bits.pc := in.bits.pc
    out.valid := false.B

    val s_wait_pc :: s_wait_mem :: s_wait_exec :: Nil = Enum(3)
    val status = RegInit(s_wait_exec)
    // printf(cf"status: ${status}\n")
    switch(status){
        is(s_wait_exec){
            in.ready := true.B
            when(in.valid){
                status := s_wait_mem
            }
        }
        is(s_wait_mem){
            out.valid := true.B
            inst := pmem_read_inst.io.rdata
            when(out.fire){
                status := s_wait_exec
            }
        }
    }


}