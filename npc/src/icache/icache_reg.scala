package npc.icache

import chisel3._
import chisel3.util._
import npc.PMU._

class ysyx_26030092_icache_reg(block_size :Int, block_num :Int) extends Module{
    val cache = Reg(Vec(block_num, UInt((block_size*8).W)))
    val offset_bits = log2Ceil(block_size/4)
    val index_bits = log2Ceil(block_num)
    val tag_bits = 30 - offset_bits - index_bits
    val tag_array = Reg(Vec(block_num, UInt(tag_bits.W)))
    val state_array = RegInit(VecInit(Seq.fill(block_num)(false.B)))

    val req = IO(new Bundle {
        val addr = Input(UInt(32.W))
    })
    val resp = IO(new Bundle {
        val data = Output(UInt(32.W))
        val hit = Output(Bool())
    })
    val refill = IO(new Bundle {
        val addr = Input(UInt(32.W))
        val block = Input(UInt((block_size*8).W))
        val valid = Input(Bool())
    })
    val flush_cache = IO(Input(Bool()))
    val flush = IO(Input(Bool()))

    // icahce 组合读
    val req_addr = req.addr
    val offset = req_addr(offset_bits + 1, 2)
    val index = req_addr(offset_bits + index_bits + 1, offset_bits + 2)
    dontTouch(offset)
    dontTouch(index)
    val block = cache(index)
    val taget_tag = tag_array(index)

    val tag = req_addr(31, offset_bits + index_bits + 2)
    dontTouch(tag)
    val block_words = VecInit.tabulate(block_size/4)(i => {
        block((i + 1) * 32 - 1, i * 32)
    })
    val resp_data = block_words(offset)
    val resp_hit = (tag === taget_tag) && state_array(index)


    resp.data := resp_data
    resp.hit := resp_hit

    // refill
    val refill_index = refill.addr(offset_bits + index_bits + 1, offset_bits + 2)
    val refill_tag = refill.addr(31, offset_bits + index_bits + 2)
    dontTouch(refill_index)
    dontTouch(refill_tag)
    when(refill.valid){ // 下一周期refill
        cache(refill_index) := refill.block
        tag_array(refill_index) := refill_tag
        state_array(refill_index) := true.B
    }

    when(flush_cache){ // flush cache优先级最大
        state_array.foreach(_ := false.B)
    }

}
