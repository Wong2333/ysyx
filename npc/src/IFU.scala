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
    val io = IO(new Bundle {
        val rst = Input(Bool())
        val pc_next = Input(UInt(32.W))
        val pc_we = Input(Bool())
        val pc = Output(UInt(32.W))
        val inst = Output(UInt(32.W))
    })
    val PC = RegInit("h80000000".U(32.W))
    val pmem_read_inst = Module(new PmemReadBlackBox)
    io.pc := PC
    pmem_read_inst.io.raddr := PC
    
    when(io.rst){
        PC := "h80000000".U
    }.otherwise{
        when(io.pc_we){
            PC := io.pc_next
        }.otherwise{
            PC := PC + 4.U
        }
    }
    io.inst := pmem_read_inst.io.rdata
}