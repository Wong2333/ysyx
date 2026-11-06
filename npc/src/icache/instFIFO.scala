package npc.icache

import chisel3._
import chisel3.util._

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