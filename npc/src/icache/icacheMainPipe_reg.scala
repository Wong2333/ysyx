package npc.icache
import chisel3._
import chisel3.util._
import npc.AXI._
import npc.PMU._
import chisel3.experimental.dataview.DataViewable

class icacheMainPipe_reg(block_size :Int, block_num :Int) extends Module{

    val icache = Module(new icache_reg(block_size, block_num))
    icache.refill.valid := false.B
    icache.refill.addr := DontCare
    icache.refill.block := DontCare

    val flush = IO(Input(Bool()))

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

    val req_fifo = Module(new Queue(UInt(30.W), 4))
    req_fifo.io.enq.valid := req.valid
    req.ready := req_fifo.io.enq.ready
    req_fifo.io.enq.bits := req.addr(31,2)

    icache.req.addr := Cat(req_fifo.io.deq.bits, 0.U(2.W))
    val hit = icache.resp.hit & req_fifo.io.deq.valid


    // val fifo = Module(new InstFIFO(4))

    // icache.req.addr := req.addr
    // req.ready := fifo.io.init.ready
    // when(req.valid && req.ready){ // icache 请求入队
    //     fifo.io.init.valid := true.B
    //     when(fifo.io.init.ready){ // fifo 写端口
    //         fifo.io.init.bits.entry := req.addr
    //         fifo.io.init.bits.needRefill := icache.resp.hit
    //         fifo.io.init.bits.entry.rdata := icache.resp.data
    //         fifo.io.init.bits.entry.excp_trap := DontCare
    //         fifo.io.init.bits.entry.excp_code := DontCare
    //     }
    // }

    val refill_unit = Module(new icacheRefillUnit_reg(block_size))
    refill_unit.req.valid := false.B
    refill_unit.flush := flush
    refill_unit.req.addr := Cat(req_fifo.io.deq.bits, 0.U(2.W))
    val refilling = RegInit(false.B) // 标记miss的cache是否refilling

    when(!hit){
      refill_unit.req.valid := !refilling
      refilling := true.B 
    }.otherwise{
      refilling := false.B
    }

    icache.refill.addr := Cat(req_fifo.io.deq.bits, 0.U(2.W))
    icache.refill.block := refill_unit.resp.block
    icache.refill.valid := refill_unit.resp.valid

    resp.raddr := Cat(req_fifo.io.deq.bits, 0.U(2.W))
    resp.rdata := icache.resp.data
    resp.valid := hit
    req_fifo.io.deq.ready := resp.ready

    val AXI4_port = IO(new AXI4Bundle)
    AXI4_port <> refill_unit.AXI4_port

    // 错误处理
    val excp_code = Reg(UInt(32.W))
    val excp_trap = RegInit(false.B)
    when(AXI4_port.rvalid && AXI4_port.rready && AXI4_port.rresp =/= 0.U){
      excp_code := 0.U
      excp_trap := true.B
    }
    when(resp.valid & resp.ready){
      excp_trap := false.B
    }
    resp.excp_code := 0.U
    resp.excp_trap := excp_trap
}
