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

    // 本次 refill 锁存的行地址。阵列写入/同一行判定必须用它，不能用上面的 index/tag：
    // CWF 早放行后前端会继续往前跑，cache_raddr 可能已经换成别的行，用它写阵列会把
    // 旧 burst 的数据按新行的 tag/index 写进去（缓存被污染）。
    val refill_index = refill_unit.addr_out(offset_bits + index_bits - 1, offset_bits)
    val refill_tag   = refill_unit.addr_out(31, offset_bits + index_bits)
    // 响应槽里压着的那个缺失，是否正是这次 refill 的行
    val pending_miss_same_line = cache_rdata_valid && !cache_hit &&
                                 tag === refill_tag && index === refill_index

    // refill 宽限窗口：miss 检测后先等 refill_grace 拍再发 refill 请求。
    // 错误路径取指的 flush 就在它自己的 miss 检测同拍或之后 1~4 拍到达，落在窗口里就
    // 整笔抑制（refill 单元连状态机都不进，一个 AR 都不发）；代价是每次真缺失多几拍。
    val refill_grace = 2
    val none_refill :: wait_grace :: wait_refill :: Nil = Enum(3)
    val refill_state = RegInit(none_refill)
    val grace_cnt = RegInit(0.U((log2Ceil(refill_grace + 2)).W))
    refilling := (refill_state =/= none_refill)
    switch(refill_state){
        is(none_refill){
            when(miss){
                refill_state := wait_grace
                grace_cnt := refill_grace.U
            }
        }
        is(wait_grace){
            when(grace_cnt === 0.U && !flush){
                refill_unit.req.valid := true.B
                when(refill_unit.req.valid & refill_unit.req.ready){
                    refill_state := wait_refill
                }
            }.otherwise{
                grace_cnt := grace_cnt - 1.U
            }
        }
        is(wait_refill){
            // CWF（early restart）：CPU 要的那个字一到就放行，不等整行。
            // 此时行还没写进阵列 —— 前端继续往前取会再 miss，那个在途缺失会由
            // icache 里的 pending_rehit（同一行、整行落地当拍）或下面这段整行交付补上。
            when(refill_unit.beat.valid && refill_unit.beat.index === offset && pending_miss_same_line){
                cache_rdata := refill_unit.beat.data
                cache_hit := true.B
            }
            when(refill_unit.resp.valid){
                val refill_block = refill_unit.resp.block
                val block_words = VecInit.tabulate(block_size/4)(i => {
                    refill_block((i + 1) * 32 - 1, i * 32)
                })
                icache.refill.valid := true.B
                icache.refill.tag := refill_tag
                icache.refill.index := refill_index
                icache.refill.block := refill_block
                // 整行落地：若响应槽里还压着这条行的缺失（CWF 后前端又踩到同一条行），
                // 用刚取回的 block 里对应的那个字交付；压着别的行/已经是命中就什么都不做
                when(pending_miss_same_line){
                    cache_rdata := block_words(offset)
                    cache_hit := true.B
                }
                refill_state := none_refill
            }
        }
    }

    when(flush) { 
        cache_rdata_valid := false.B
        refill_state := none_refill
        grace_cnt := 0.U
    }

    // 归因用：一次错误路径 miss 的 flush 若落在同拍或 1~4 拍内，分别进下面三个计数；
    // 三者之和就是被抑制掉的"白烧通道"的 refill 数
    // CWF 早放行次数（每次 refill 最多 1 次）
    PerfCounter("icache_refill_cwf") := (refill_state === wait_refill) && refill_unit.beat.valid &&
                                        refill_unit.beat.index === offset && pending_miss_same_line
    PerfCounter("icache_refill_flushed") := (refill_state === wait_refill) && flush
    PerfCounter("icache_refill_grace_killed") := (refill_state === wait_grace) && flush
    PerfCounter("icache_refill_suppressed") := miss && flush && refill_unit.req.ready
    PerfCounter("icache_miss_killed_by_flush") := flush && icache.resp.valid && !icache.resp.hit
    PerfCounter("icache_stall_by_refill") := (refill_state === wait_refill)
    PerfCounter("icache_refill_window") := (refill_state === wait_grace)
    PerfCounter("icache_stall_by_backend") := resp.valid && !resp.ready
    PerfCounter("icache_refill_req_stall") := refill_unit.req.valid && !refill_unit.req.ready

    val AXI4_port = IO(new AXI4Bundle)
    AXI4_port <> refill_unit.AXI4_port
}
