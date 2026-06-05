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

    in.ready := false.B
    pmem_read_inst.io.raddr := in.bits.pc
    out.bits.inst := "h12345678".U
    out.bits.pc := in.bits.pc
    out.valid := false.B

    val s_reset :: s_wait_exec :: s_wait_mem :: Nil = Enum(3)
    val status = RegInit(s_reset)
    val cycle = RegInit(0.U(32.W))
    cycle := cycle + 1.U
    printf("cycle: %x status: %x pc: %x inst: %x out.valid: %x\n",cycle,status,out.bits.pc,out.bits.inst,out.valid)

    switch(status){
        is(s_reset){
            status := s_wait_exec
        }
        is(s_wait_exec){
            in.ready := true.B
            when(in.valid){
                status := s_wait_mem
            }
        }
        is(s_wait_mem){
            out.valid := true.B
            out.bits.inst := pmem_read_inst.io.rdata
            when(out.fire){
                status := s_wait_exec
            }
        }
    }


}