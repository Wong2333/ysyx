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
    // 供 IFU 归因：当前是否卡在 refill（真缺失）而非响应槽被后端占用
    val refilling = IO(Output(Bool()))
    val req = IO(new Bundle {
        val addr = Input(UInt(32.W))
        val valid = Input(Bool())
        val ready = Output(Bool())
    })
    req.ready := false.B

    val resp = IO(new Bundle {
        val rdata = Output(UInt(32.W))
        val raddr = Output(UInt(32.W))
        val excp_code = Output(UInt(32.W))
        val excp_trap = Output(Bool())
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
    val excp_code = Reg(UInt(32.W))
    val excp_trap = RegInit(false.B)
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
            when(icache.resp.raddr(1,0)=/=0.U){
                excp_code := 0.U
                excp_trap := true.B
            }.otherwise{
                excp_trap := false.B
            }
        }
    }
    
    // PerfCounter("icache_hit") := req_valid_s1 && icache.resp.hit
    val miss = cache_rdata_valid && !cache_hit
    val refill_unit = Module(new icacheRefillUnit(block_size))
    refill_unit.flush := flush
    refill_unit.req.addr := cache_raddr
    refill_unit.req.valid := false.B

    val refill_done = refill_unit.resp.valid
    resp.valid := (cache_rdata_valid & cache_hit) & ~flush
    
    resp.rdata := cache_rdata
    resp.raddr := cache_raddr
    resp.excp_code := excp_code
    resp.excp_trap := excp_trap
    cache_rdata_ready := resp.ready



    // 事件计数：miss 上升沿（每 miss 一次）；hit = 命中响应被消费拍
    val miss_prev = RegNext(miss, false.B)
    PerfCounter("icache_miss") := icache.resp.valid && icache.resp.ready && !icache.resp.hit
    PerfCounter("icache_hit") := icache.resp.valid && icache.resp.ready && icache.resp.hit


    val offset_bits = log2Ceil(block_size)
    val index_bits = log2Ceil(block_num)
    val tag_bits = 32 - offset_bits - index_bits
    val offset = if(offset_bits <= 2) 0.U(1.W) else cache_raddr(offset_bits-1,2)
    val index = cache_raddr(offset_bits + index_bits-1, offset_bits)
    val tag = cache_raddr(31, offset_bits + index_bits)

    // 缺失 → 立刻发 refill 请求；但同拍有 flush 就整笔抑制（refill 单元一旦握手就会把
    // 整条 AXI burst 发出去、不可取消，而 mainPipe 已被 flush 打回 none_refill，拿不回数据）。
    // 注：这里原来还有一个几拍的"宽限窗口"用来拦错误路径的 refill；但 flush 提前到 miss
    // 同拍附近之后 icache_refill_grace_killed 一直是 0（窗口抓不到任何东西），窗口就只剩
    // "每次真缺失多等几拍"的成本了，故去掉。
    val none_refill :: wait_refill :: Nil = Enum(2)
    val refill_state = RegInit(none_refill)
    refilling := (refill_state === wait_refill)
    switch(refill_state){
        is(none_refill){
            when(miss && !flush){
                refill_unit.req.valid := true.B
                when(refill_unit.req.valid & refill_unit.req.ready){
                    refill_state := wait_refill
                }
            }
        }
        is(wait_refill){
            when(refill_unit.resp.valid){
                val refill_block = refill_unit.resp.block
                val block_words = VecInit.tabulate(block_size/4)(i => {
                    refill_block((i + 1) * 32 - 1, i * 32)
                })
                icache.refill.valid := true.B
                icache.refill.tag := tag
                icache.refill.index := index
                icache.refill.block := refill_block
                cache_rdata := block_words(offset)
                cache_hit := true.B
                refill_state := none_refill
            }
        }
    }

    when(flush) { 
        cache_rdata_valid := false.B
        refill_state := none_refill
    }

    // 归因用：一次错误路径 miss 的 flush 若落在同拍或 1~4 拍内，分别进下面三个计数；
    // 三者之和就是被抑制掉的"白烧通道"的 refill 数
    PerfCounter("icache_refill_flushed") := (refill_state === wait_refill) && flush
    PerfCounter("icache_refill_suppressed") := miss && flush && refill_unit.req.ready
    PerfCounter("icache_miss_killed_by_flush") := flush && icache.resp.valid && !icache.resp.hit
    PerfCounter("icache_stall_by_refill") := (refill_state === wait_refill)
    PerfCounter("icache_stall_by_backend") := resp.valid && !resp.ready
    PerfCounter("icache_refill_req_stall") := refill_unit.req.valid && !refill_unit.req.ready

    val AXI4_port = IO(new AXI4Bundle)
    AXI4_port <> refill_unit.AXI4_port
}
