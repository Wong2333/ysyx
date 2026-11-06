package npc.icache
import chisel3._
import chisel3.util._
import npc.AXI._
import npc.PMU._

class icacheMainPipe_reg(block_size :Int, block_num :Int) extends Module{

    val icache = Module(new icache_reg(block_size, block_num))
    icache.refill.valid := false.B
    icache.refill.index := DontCare
    icache.refill.tag := DontCare
    icache.refill.block := DontCare

    val flush = IO(Input(Bool()))
    val refilling = IO(Output(Bool()))
    val req = IO(new Bundle {
        val addr = Input(UInt(32.W))
        val valid = Input(Bool())
        val ready = Output(Bool())
    })

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

    val fifo = Module(new InstFIFO(4))

    icache.req.addr := req.addr
    req.ready := fifo.io.init.ready
    when(req.valid && req.ready){ // icache 请求入队
        fifo.io.init.valid := true.B
        when(fifo.io.init.ready){ // fifo 写端口
            fifo.io.init.bits.entry := req.addr
            fifo.io.init.bits.needRefill := icache.resp.hit
            fifo.io.init.bits.entry.rdata := icache.resp.data
            fifo.io.init.bits.entry.excp_trap := DontCare
            fifo.io.init.bits.entry.excp_code := DontCare
        }
    }

    val refill_unit = Module(new icacheRefillUnit_reg(block_size))
    refill_unit.req.valid := fifo.io.refill.need_refill
    refill_unit.req.addr := fifo.io.refill.raddr
    when(refill_unit.req.ready & refill_unit.req.valid){
        
    }


    val AXI4_port = IO(new AXI4Bundle)
    AXI4_port <> refill_unit.AXI4_port
}
