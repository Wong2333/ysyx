package npc.icache

import chisel3._
import chisel3.util._

class icache(block_size :Int, block_num :Int) extends Module{
    val cache = SyncReadMem(block_num, UInt((block_size*8).W))
    val offset_bits = log2Ceil(block_size)
    val index_bits = log2Ceil(block_num)
    val tag_bits = 32 - offset_bits - index_bits
    val tag_array = SyncReadMem(block_num, UInt(tag_bits.W))
    val state_array = RegInit(VecInit(Seq.fill(block_num)(false.B)))

    val req = IO(new Bundle {
        val addr = Input(UInt(32.W))
        val valid = Input(Bool())
        val ready = Output(Bool())
    })
    val resp = IO(new Bundle {
        val raddr = Output(UInt(32.W))
        val data = Output(UInt(32.W))
        val hit = Output(Bool())
        val valid = Output(Bool())
        val ready = Input(Bool())
    })
    val refill = IO(new Bundle {
        val block = Input(UInt((block_size*8).W))
        val tag = Input(UInt(tag_bits.W))
        val index = Input(UInt(index_bits.W))
        val valid = Input(Bool())
    })
    val flush_cache = IO(Input(Bool()))
    val flush = IO(Input(Bool()))

    val req_addr = req.addr
    val index = req_addr(offset_bits + index_bits-1, offset_bits)

    val block = cache.read(index)
    val taget_tag = tag_array.read(index)

    val req_addr_reg   = RegEnable(req.addr, req.valid & req.ready)
    val req_addr_valid = RegNext(req.valid & req.ready & ~flush, false.B)

    val offset_s1 = if(offset_bits <= 2) 0.U(1.W) else req_addr_reg(offset_bits-1,2)
    val index_s1 = req_addr_reg(offset_bits + index_bits-1, offset_bits)
    val tag_s1 = req_addr_reg(31, offset_bits + index_bits)

    val resp_new = req_addr_valid
    val resp_new_data = (block >> (offset_s1 << 5))(31,0)
    val resp_new_hit = (tag_s1 === taget_tag) && state_array(index_s1)

    val cache_rdata_reg = Reg(UInt(32.W))
    val cache_raddr_reg = Reg(UInt(32.W))
    val cache_hit_reg = Reg(Bool())
    val cache_rdata_valid = RegInit(false.B)
    dontTouch(cache_rdata_reg)
dontTouch(req_addr_reg)
    val cache_rdata_fire = (cache_rdata_valid | resp_new) & resp.ready
    when(cache_rdata_fire | ~flush){ 
        cache_rdata_valid := false.B 
    }
    .elsewhen(resp_new){ 
        cache_rdata_valid := true.B; 
        cache_rdata_reg := resp_new_data; 
        cache_raddr_reg := req_addr_reg;
        cache_hit_reg := resp_new_hit 
    }

    req.ready := ~(cache_rdata_valid | resp_new) | cache_rdata_fire

    resp.data := Mux(resp_new, resp_new_data, cache_rdata_reg)
    resp.hit := Mux(resp_new, resp_new_hit, cache_hit_reg)
    resp.raddr := Mux(resp_new, req_addr_reg, cache_raddr_reg)
    resp.valid := (cache_rdata_valid | resp_new) & ~flush

    when(refill.valid){
        cache.write(refill.index, refill.block)
        tag_array.write(refill.index, refill.tag)
        state_array(refill.index) := true.B
    }
    when(flush_cache){
        state_array.foreach(_ := false.B)
    }

}
