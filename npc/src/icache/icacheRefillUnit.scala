package npc.icache

import chisel3._
import chisel3.util._
import npc.AXI._
import npc.PMU._

class icacheRefillUnit(block_size :Int) extends Module{
    val flush = IO(Input(Bool()))
    val req = IO(new Bundle {
        val addr = Input(UInt(32.W))
        val valid = Input(Bool())
        val ready = Output(Bool())
    })
    req.ready := false.B

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
    // 一次 INCR burst 取回整个 block：beats 个 32 位 beat
    val beats = block_size / 4

    // CWF（critical word first / early restart）：把逐 beat 的到达时刻/序号/数据引出来，
    // 另外把本次 refill 锁存的行地址引出来 —— 行地址必须用锁存的，不能用"当前"响应地址：
    // 早放行之后前端会往前跑，cache_raddr 可能已经换到别的行。
    val idx_w = math.max(1, log2Ceil(beats))
    val beat = IO(new Bundle {
        val valid = Output(Bool())
        val index = Output(UInt(idx_w.W))
        val data  = Output(UInt(32.W))
    })
    val addr_out = IO(Output(UInt(32.W)))
    val beat_cnt = RegInit(0.U(idx_w.W))
    beat.valid := (state === s_wait_mem_data) && AXI4_port.rvalid
    beat.index := beat_cnt
    beat.data  := AXI4_port.rdata
    addr_out   := req_addr_reg

    when(state === s_idle && req.valid){
        req.ready := true.B
        state := s_wait_mem_free
        req_addr_reg := block_addr
        beat_cnt := 0.U
    }
    val block_reg = Reg(UInt((block_size*8).W))
    val block_valid = RegInit(false.B)

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
            when(AXI4_port.rvalid){
                AXI4_port.rready := true.B
                beat_cnt := beat_cnt + 1.U
                // beat0 在最高 32 位，后续 beat 依次右移拼接
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