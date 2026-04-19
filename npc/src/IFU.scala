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

class PCBlackBox extends ExtModule{
    val io = FlatIO(new Bundle {
        val clock = Input(Clock())
        val reset = Input(Reset())
        val pc_next = Input(UInt(32.W))
        val pc_we = Input(Bool())
        val pc = Output(UInt(32.W))
    })
    addResource("/PCBlockBox.v")
}

class IFU extends Module{
    val io = IO(new Bundle {
        val pc_next = Input(UInt(32.W))
        val pc_we = Input(Bool())
        val pc = Output(UInt(32.W))
        val inst = Output(UInt(32.W))
    })
    val PC = Module(new PCBlackBox())
    PC.io.clock := clock
    PC.io.reset := reset
    PC.io.pc_next := io.pc_next
    PC.io.pc_we := io.pc_we
    io.pc := PC.io.pc

    val pmem_read_inst = Module(new PmemReadBlackBox)
    pmem_read_inst.io.raddr := PC.io.pc

    io.inst := pmem_read_inst.io.rdata
    

}