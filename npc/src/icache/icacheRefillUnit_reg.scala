package npc.icache

import chisel3._
import chisel3.util._
import npc.AXI._
import npc.PMU._

class icacheRefillUnit_reg(block_size :Int) extends Module{
    val flush = IO(Input(Bool()))
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
    PerfCounter("icache_refill_count") := (state === s_idle) && req.valid
    val req_addr_reg = Reg(UInt(32.W))

    val block_bits = log2Ceil(block_size)
    val block_addr = Cat(req.addr(31,block_bits),0.U(block_bits.W))
    
    val beats = block_size / 4 //需要beats拍传回数据

    val block_reg = Reg(UInt((block_size*8).W))
    val block_valid = RegInit(false.B)

    switch(state){
        is(s_idle){
            when(req.valid){
                req_addr_reg := block_addr
                state := s_wait_mem_free
            }
        }
        is(s_wait_mem_free){
            AXI4_port.arvalid := true.B
            AXI4_port.araddr := req_addr_reg
            AXI4_port.arlen := (beats - 1).U
            AXI4_port.arsize := 2.U
            AXI4_port.arburst := 1.U
            when(AXI4_port.arready){
                state := s_wait_mem_data
            }
        }
        is(s_wait_mem_data){
            when(AXI4_port.rvalid){
                AXI4_port.rready := true.B
                if(block_size > 4){
                    block_reg := Cat(AXI4_port.rdata, block_reg(block_size*8 - 1, 32))
                }else{
                    block_reg := AXI4_port.rdata
                }

                when(AXI4_port.rlast){
                    state := s_idle
                    block_valid := true.B
                }
            }
        }
    }

    resp.block := block_reg
    resp.valid := block_valid


    when(block_valid){ block_valid := false.B }

}