package npc.icache

import chisel3._
import chisel3.util._
import npc.AXI._
import npc.PMU._

class icacheRefillUnit(block_size :Int) extends Module{
    val req = IO(new Bundle {
        val addr = Input(UInt(32.W))
        val valid = Input(Bool())
    })

    val resp = IO(new Bundle {
        val block = Output(UInt((block_size*8).W))
        val valid = Output(Bool())
    })
    resp.block := DontCare
    resp.valid := false.B

    val AXI4_port = IO(new AXI4Bundle)
    AXI4_port.initMaster()

    val s_idle :: s_wait_mem_free :: s_wait_mem_data :: Nil = Enum(3)
    val state = RegInit(s_idle)
    PerfCounter("icache_refill_busy") := (state =/= s_idle)
    val req_addr_reg = Reg(UInt(32.W))

    val block_bits = log2Ceil(block_size)
    val block_addr = Cat(req.addr(31,block_bits),0.U(block_bits.W))
    // 一次 INCR burst 取回整个 block：beats 个 32 位 beat
    val beats = block_size / 4

    when(state === s_idle && req.valid){
        state := s_wait_mem_free
        req_addr_reg := block_addr
    }
    val block_reg = Reg(UInt((block_size*8).W))

    switch(state){
        is(s_wait_mem_free){
            AXI4_port.arvalid := true.B
            AXI4_port.araddr := req_addr_reg
            AXI4_port.arlen := (beats - 1).U
            AXI4_port.arsize := 2.U
            AXI4_port.arburst := 1.U  // INCR
            when(AXI4_port.arready){
                state := s_wait_mem_data
            }
        }
        is(s_wait_mem_data){
            AXI4_port.rready := true.B
            when(AXI4_port.rvalid){
                // beat0 在最高 32 位，后续 beat 依次右移拼接
                block_reg := Cat(AXI4_port.rdata, block_reg(block_size*8 - 1, 32))
                when(AXI4_port.rlast){
                    state := s_idle
                    resp.valid := true.B
                    resp.block := Cat(AXI4_port.rdata, block_reg(block_size*8 - 1, 32))
                }
            }
        }
    }
}