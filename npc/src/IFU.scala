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
    addResource("/PCBlackBox.v")
}

class IFU extends Module{

    val in = IO(new Bundle {
        val pc_next = Input(UInt(32.W))
        val pc_we = Input(Bool())
    })
    val out = IO(new Bundle {
        val pc = Output(UInt(32.W))
        val inst = Output(UInt(32.W))
    })
    val PC_reg = Module(new PCBlackBox())
    PC_reg.io.clock := clock
    PC_reg.io.reset := reset
    PC_reg.io.pc_next := in.pc_next
    PC_reg.io.pc_we := in.pc_we
    out.pc := PC_reg.io.pc

    val pmem_read_inst = Module(new PmemReadBlackBox)
    pmem_read_inst.io.raddr := PC_reg.io.pc

    out.inst := pmem_read_inst.io.rdata
    

}