package npc.icache
import chisel3._
import chisel3.util._
import npc.AXI._
import npc.PMU._

class icacheMainPipe(block_size :Int, block_num :Int) extends Module{

    val icache = Module(new icache(block_size, block_num))
    icache.refill.valid := false.B
    icache.refill.index := DontCare
    icache.refill.tag := DontCare
    icache.refill.block := DontCare

    val flush = IO(Input(Bool()))
    val req = IO(new Bundle {
        val addr = Input(UInt(32.W))
        val valid = Input(Bool())
        val ready = Output(Bool())
    })
    req.ready := false.B

    val resp = IO(new Bundle {
        val rdata = Output(UInt(32.W))
        val raddr = Output(UInt(32.W))
        val valid = Output(Bool())
        val ready = Input(Bool())
    })

    val icache_flush = IO(Input(Bool()))
    icache.flush_cache := icache_flush
    icache.flush := flush
    icache.resp.ready := false.B

    icache.req.valid := req.valid
    req.ready := icache.req.ready
    icache.req.addr := req.addr

    val cache_rdata = Reg(UInt(32.W))
    val cache_raddr = Reg(UInt(32.W))
    val cache_hit = RegInit(false.B)
    val cache_rdata_valid = RegInit(false.B)
    val cache_rdata_ready = WireDefault(false.B)
    val cache_rdata_fire = resp.valid & cache_rdata_ready
    dontTouch(cache_raddr)
    dontTouch(cache_rdata)
    dontTouch(cache_hit)
    dontTouch(cache_rdata_valid)
    when(cache_rdata_fire || ~cache_rdata_valid){
        cache_rdata_valid := false.B
        when(icache.resp.valid){
            cache_rdata_valid := true.B
            icache.resp.ready := true.B
            cache_rdata := icache.resp.data
            cache_raddr := icache.resp.raddr
            cache_hit := icache.resp.hit
        }
    }
    when(flush) { cache_rdata_valid := false.B}
    // PerfCounter("icache_hit") := req_valid_s1 && icache.resp.hit
    val miss = cache_rdata_valid && !cache_hit
    val refill_unit = Module(new icacheRefillUnit(block_size))
    refill_unit.flush := flush
    refill_unit.req.addr := cache_raddr
    val refilling = RegInit(false.B)
    val wait_miss_block = RegInit(false.B)
    when(miss){
        refill_unit.req.valid := true.B
        when(refill_unit.req.ready){
            wait_miss_block := true.B
        }
    }
    val refill_done = refill_unit.resp.valid
    resp.valid := (cache_rdata_valid & cache_hit) & ~flush
    
    resp.rdata := cache_rdata
    resp.raddr := cache_raddr
    cache_rdata_ready := resp.ready



    PerfCounter("icache_miss") := miss


    val offset_bits = log2Ceil(block_size)
    val index_bits = log2Ceil(block_num)
    val tag_bits = 32 - offset_bits - index_bits
    val offset = if(offset_bits <= 2) 0.U(1.W) else cache_raddr(offset_bits-1,2)
    val index = cache_raddr(offset_bits + index_bits-1, offset_bits)
    val tag = cache_raddr(31, offset_bits + index_bits)

    when(refill_done){
        val refill_block = refill_unit.resp.block
        wait_miss_block := false.B
        icache.refill.valid := true.B
        icache.refill.tag := tag
        icache.refill.index := index
        icache.refill.block := refill_block
        cache_rdata := (refill_block >> (offset << 5))(31,0)
        cache_hit := true.B
    }

    val AXI4_port = IO(new AXI4Bundle)
    AXI4_port <> refill_unit.AXI4_port
}
