package npc

import chisel3._
import chisel3.util._

class PmemReadBlackBox extends ExtModule{
    val io = FlatIO(new Bundle{
        val raddr = Input(UInt(32.W))
        val rdata = Output(UInt(32.W))
    })
    addResource("/PmemReadBlackBox.v")
}

class IFU extends Module{

    val in = IO(new Bundle {
        val pc = Input(UInt(32.W))
    })
    val out = IO(new Bundle {
        val inst = Output(UInt(32.W))
    })

    val pmem_read_inst = Module(new PmemReadBlackBox)

    pmem_read_inst.io.raddr := in.pc
    // printf(cf"inst ${pmem_read_inst.io.rdata}\n")
    out.inst := pmem_read_inst.io.rdata
    

}