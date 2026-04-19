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
        val pc_next = Input(UInt(32.W))
        val pc_we = Input(Bool())
        val pc = Output(UInt(32.W))
        val inst = Output(UInt(32.W))
    })
    val PC = RegInit("h00000000".U(32.W))
    val pmem_read_inst = Module(new PmemReadBlackBox)
    io.pc := PC
    pmem_read_inst.io.raddr := PC

    io.inst := pmem_read_inst.io.rdata
    when(reset.asBool){
        io.inst := 0.U
    }.elsewhen(io.pc_we){
        PC := io.pc_next
    }.otherwise{
        PC := PC + 4.U
    }
    

}