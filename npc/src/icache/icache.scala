package npc.icache

import chisel3._
import chisel3.util._
import os.Source.WritableSource

class icache(block_size :Int, block_num :Int) extends Module{
    val cache = SyncReadMem(block_num, UInt((block_size*8).W))
    val offset_bits = log2Ceil(block_size)
    val index_bits = log2Ceil(block_num)
    val tag_bits = 32 - offset_bits - index_bits
    val tag_array = SyncReadMem(block_num, UInt(tag_bits.W))
    val state_array = RegInit(VecInit(Seq.fill(block_num)(false.B)))

    val req = IO(new Bundle {
        val addr = Input(UInt(32.W))
    })
    val resp = IO(new Bundle {
        val data = Output(UInt(32.W))
        val hit = Output(Bool())
    })
    val refill = IO(new Bundle {
        val block = Input(UInt((block_size*8).W))
        val tag = Input(UInt(tag_bits.W))
        val index = Input(UInt(index_bits.W))
        val valid = Input(Bool())
    })
    val flush = IO(Input(Bool()))

    val req_addr = req.addr
    val offset = if(offset_bits <=2 ) 0.U(1.W) else req_addr(offset_bits-1,2)
    val index = req_addr(offset_bits + index_bits-1, offset_bits)
    val tag = req_addr(31, offset_bits + index_bits)

    val block = cache.read(index)
    val taget_tag = tag_array.read(index)

    resp.data := (block >> (offset << 5))(31,0)
    resp.hit := (tag === taget_tag) && state_array(index)

    when(refill.valid){
        cache.write(refill.index, refill.block)
        tag_array.write(refill.index, refill.tag)
        state_array(refill.index) := true.B
    }
    when(flush){
        state_array.foreach(_ := false.B)
    }

}