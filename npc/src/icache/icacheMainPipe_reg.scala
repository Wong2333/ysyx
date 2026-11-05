package npc.icache
import chisel3._
import chisel3.util._
import npc.AXI._
import npc.PMU._

class instEntry extends Bundle {
  val rdata     = UInt(32.W)
  val raddr     = UInt(32.W)
  val excp_code = UInt(32.W)
  val excp_trap = Bool()
}

class InstFIFO(depth: Int) extends Module {
  require(depth > 0, "depth must be positive")

  val io = IO(new Bundle {
    val init = Flipped(Decoupled(new Bundle {
      val entry      = new instEntry
      val needRefill = Bool()
    }))
    val refill = new Bundle {
      val valid     = Input(Bool())
      val rdata     = Input(UInt(32.W))
      val excp_code = Input(UInt(32.W))
      val excp_trap = Input(Bool())
      val need_refill     = Output(Bool())
      val raddr = Output(Bool())
    }

    val deq = Decoupled(new instEntry)
  })

  val sEmpty :: sWaitRefill :: sReady :: Nil = Enum(3)
  val mem   = Reg(Vec(depth, new instEntry))
  val state = RegInit(VecInit(Seq.fill(depth)(sEmpty)))


  val wrPtr = Counter(depth)  
  val rfPtr = Counter(depth) 
  val rdPtr = Counter(depth)

  val wrSlotEmpty = state(wrPtr.value) === sEmpty
  val rfWaiting   = state(rfPtr.value) === sWaitRefill
  val rdSlotReady = state(rdPtr.value) === sReady

  io.init.ready   := wrSlotEmpty
  io.refill.need_refill := rfWaiting
  io.refill.raddr := mem(rfPtr.value).raddr
  io.deq.valid    := rdSlotReady
  io.deq.bits     := mem(rdPtr.value)

  val doInit   = io.init.fire
  val doRefill = io.refill.valid && rfWaiting
  val doRead   = io.deq.fire

  when(doInit) {
    mem(wrPtr.value)       := io.init.bits.entry
    state(wrPtr.value)     := Mux(io.init.bits.needRefill, sWaitRefill, sReady)
    wrPtr.inc()
  }

  when(doRefill) {
    mem(rfPtr.value).rdata     := io.refill.rdata
    mem(rfPtr.value).excp_code := io.refill.excp_code
    mem(rfPtr.value).excp_trap := io.refill.excp_trap
    state(rfPtr.value) := sReady
    rfPtr.inc()
  }

  when(!rfWaiting && rfPtr.value =/= wrPtr.value) {
    rfPtr.inc()
  }

  when(doRead) {
    state(rdPtr.value) := sEmpty
    rdPtr.inc()
  }
}



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
    when(req.valid && req.ready){
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
