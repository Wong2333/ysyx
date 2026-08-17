package npc.icache
import chisel3._
import chisel3.util._
import npc.AXI._
import npc.PMU._
import dataclass.data
import chisel3.simulator.PeekPokeAPI.TestableData

class icacheMainPipe(block_size :Int, block_num :Int) extends Module{

    val icache = Module(new icache(block_size, block_num))
    icache.refill.valid := false.B
    icache.refill.index := DontCare
    icache.refill.tag := DontCare
    icache.refill.block := DontCare

    val req = IO(new Bundle {
        val addr = Input(UInt(32.W))
        val valid = Input(Bool())
        val ready = Output(Bool())
    })

    val resp = IO(new Bundle {
        val rdata = Output(UInt(32.W))
        val valid = Output(Bool())
        val ready = Input(Bool())
    })

    val icache_flush = IO(Input(Bool()))
    icache.flush := icache_flush

    val req_addr_s1 = RegNext(req.addr)
    val req_valid_s1 = RegNext(req.valid & req.ready, false.B)

    val s_idle :: s_busy :: Nil = Enum(2)

    val state = RegInit(s_idle)
    req.ready := Mux(state === s_idle, true.B, false.B)

    icache.req.addr := req.addr

    resp.valid := req_valid_s1 && icache.resp.hit
    resp.rdata := icache.resp.data

    PerfCounter("icache_hit") := req_valid_s1 && icache.resp.hit
    val miss_s1 = req_valid_s1 && !icache.resp.hit

    PerfCounter("icache_miss") := miss_s1
    val refill_unit = Module(new icacheRefillUnit(block_size))
    refill_unit.req.addr := req_addr_s1
    refill_unit.req.valid := miss_s1


    val offset_bits = log2Ceil(block_size)
    val index_bits = log2Ceil(block_num)
    val tag_bits = 32 - offset_bits - index_bits
    val offset_s1 = if(offset_bits <= 2) 0.U(1.W) else req_addr_s1(offset_bits-1,2)
    val index_s1 = req_addr_s1(offset_bits + index_bits-1, offset_bits)
    val tag_s1 = req_addr_s1(31, offset_bits + index_bits)

    val miss_offset = Reg(UInt(offset_bits.W))
    val refill_tag = Reg(UInt(tag_bits.W))
    val refill_index = Reg(UInt(index_bits.W))

    when(miss_s1){
        refill_tag := tag_s1
        refill_index := index_s1
        miss_offset := offset_s1
        state := s_busy
    }

    when(refill_unit.resp.valid){
        val refill_block = refill_unit.resp.block
        icache.refill.valid := true.B
        icache.refill.tag := refill_tag
        icache.refill.index := refill_index
        icache.refill.block := refill_block
        resp.valid := true.B
        resp.rdata := (refill_block >> (miss_offset << 5))(31,0)
        state := s_idle
    }

    val AXI4_port = IO(new AXI4Bundle)
    AXI4_port <> refill_unit.AXI4_port
}