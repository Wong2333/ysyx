package npc

import chisel3._
import chisel3.util._
import npc.AXI.AXI4Bundle
import npc.PMU._

class mtrace extends ExtModule{
    val io = FlatIO(new Bundle {
        val is_write = Input(Bool())
        val addr = Input(UInt(32.W))
        val data = Input(UInt(32.W))
        val size = Input(UInt(4.W))
        val valid = Input(Bool())
        val pc = Input(UInt(32.W))
    })
    addResource("/mtrace.v")
}

class LSUBundle extends Bundle{
    val mem_op     = Output(MemOp())      // none / load / store
    val mem_size   = Output(MemSize())    // byte / half / word
    val mem_signed = Output(Bool())       // 符号扩展（仅 load 有效）
    val wdata = Output(UInt(32.W))
    val addr  = Output(UInt(32.W))
    val pc    = Output(UInt(32.W))
}

class ysyx_26030092_LSU extends Module{
    val in = IO(Flipped(Decoupled(new LSUBundle)))
    // 完成拍即报告拍：数据当场交给 EXU 的 mem_pending 锁存，本模块不再留一份
    val out = IO(Decoupled(new Bundle {
        val rdata = Output(UInt(32.W))
    }))

    val AXI4_port = IO(new AXI4Bundle)

    val to_store = in.bits.mem_op === MemOp.store

    val rd_addr = Reg(UInt(32.W))
    val rd_size = Reg(MemSize())
    val rd_signed = Reg(Bool())
    val rd_pc = Reg(UInt(32.W))
    val wr_addr = Reg(UInt(32.W))
    val wr_data = Reg(UInt(32.W))
    val wr_size = Reg(MemSize())
    val wr_pc = Reg(UInt(32.W))

    val r_idle :: r_ar :: r_data :: Nil = Enum(3)
    val r_state = RegInit(r_idle)
    val w_idle :: w_aw :: w_b :: Nil = Enum(3)
    val w_state = RegInit(w_idle)

    val wr_busy = w_state =/= w_idle
    val rd_busy = r_state =/= r_idle

    // 顺序：只有和"在途 store"落在同一个 32 位字的 load 才需要等它落地（保守按字比）
    val conflict = wr_busy && (in.bits.addr(31,2) === wr_addr(31,2))
    in.ready := Mux(to_store,
                    (w_state === w_idle) && (r_state === r_idle),
                    (r_state === r_idle) && !conflict)

    // ---------------- 运算：load 数据的移位/扩展 ----------------
    val rd_shifted = AXI4_port.rdata >> (rd_addr(1,0) << 3)
    val rdata = WireDefault(0.U(32.W))
    dontTouch(rdata)
    switch(rd_size){
        is(MemSize.byte){ rdata := Mux(rd_signed,
            Cat(Fill(24, rd_shifted(7)), rd_shifted(7,0)),
            Cat(Fill(24, 0.U), rd_shifted(7,0))) }
        is(MemSize.half){ rdata := Mux(rd_signed,
            Cat(Fill(16, rd_shifted(15)), rd_shifted(15,0)),
            Cat(Fill(16, 0.U), rd_shifted(15,0))) }
        is(MemSize.word){ rdata := rd_shifted }
    }

    // ---------------- 运算：store 数据的移位/掩码 ----------------
    val wr_shifted = wr_data << (wr_addr(1,0) << 3)
    val wr_awsize = MuxLookup(wr_size, 2.U)(Seq(
        MemSize.byte -> 0.U,
        MemSize.half -> 1.U))
    val wmask = WireDefault(0.U(4.W))
    dontTouch(wmask)
    switch(wr_size){
        is(MemSize.byte){ wmask := 1.U << wr_addr(1,0) }
        is(MemSize.half){ wmask := 3.U << wr_addr(1,0) }
        is(MemSize.word){ wmask := "b1111".U }
    }
    val mtrace_size = MuxLookup(wr_size, 4.U)(Seq(
        MemSize.byte -> 1.U,
        MemSize.half -> 2.U))

    val mtrace_u = Module(new mtrace)
    mtrace_u.io.valid := false.B
    List( mtrace_u.io.data, mtrace_u.io.is_write, mtrace_u.io.size).map(_ := 0.U)
    mtrace_u.io.addr := wr_addr
    mtrace_u.io.pc := wr_pc

    out.valid := false.B
    out.bits.rdata := rdata

    // 写通道的地址/数据各交一次
    val aw_sent = RegInit(false.B)
    val w_sent = RegInit(false.B)
    val store_done = (aw_sent || AXI4_port.awready) && (w_sent || AXI4_port.wready)

    AXI4_port.initMaster()
    // 读通道
    AXI4_port.araddr := rd_addr
    AXI4_port.arsize := MuxLookup(rd_size, 2.U)(Seq(
        MemSize.byte -> 0.U,
        MemSize.half -> 1.U))
    AXI4_port.arvalid := (r_state === r_ar)
    AXI4_port.rready := (r_state === r_data)
    // 写通道
    AXI4_port.awaddr := wr_addr
    AXI4_port.awsize := wr_awsize
    AXI4_port.awvalid := (w_state === w_aw) && !aw_sent
    AXI4_port.wdata := wr_shifted
    AXI4_port.wstrb := wmask
    AXI4_port.wlast := true.B
    AXI4_port.wvalid := (w_state === w_aw) && !w_sent
    AXI4_port.bready := (w_state === w_b)

    // 给 WBU：有 store 未落地时，fence.i 必须等（否则 icache 重填可能读到旧代码）
    val b_pending_out = IO(Output(Bool()))
    b_pending_out := wr_busy

    // ---- 阻塞拍数 = in.ready 为低的拍数（读写双通道后，这才是"流水线被 LSU 挡住"）----
    val lsu_blocked = in.valid && !in.ready
    PerfCounter("LSU_busy")       := lsu_blocked
    // store 被挡住 = 写通道忙着上一笔（通常是在等 B）
    PerfCounter("LSU_store_gate") := lsu_blocked &&  (in.bits.mem_op === MemOp.store)
    // load 被挡住 = 与在途 store 落在同一个 32 位字（顺序保护，正常应接近 0）
    PerfCounter("LSU_load_gate")  := lsu_blocked && !(in.bits.mem_op === MemOp.store)
    // ---- 在途拍数（两条通道可同时在途，不能相加当阻塞拍数看）----
    PerfCounter("LSU_load_latency")  := rd_busy
    PerfCounter("LSU_store_latency") := (w_state === w_aw)
    PerfCounter("LSU_load_wait_free")  := (r_state === r_ar)
    PerfCounter("LSU_store_wait_free") := (w_state === w_aw)
    PerfCounter("LSU_load_wait_resp")  := (r_state === r_data)
    PerfCounter("LSU_store_b_pending") := (w_state === w_b)
    // 完成拍必须被接收：EXU 在 lsu_busy 期间恒 ready
    PerfCounter("LSU_report_lost") := out.valid && !out.ready
    // 两条通道同拍报告 = 不可能（EXU 一次只压一条），出现就是 bug
    PerfCounter("LSU_two_fire") := (r_state === r_data && AXI4_port.rvalid) &&
                                   (w_state === w_aw && store_done)

    switch(r_state){
        is(r_idle){
            when(in.fire && !to_store){
                r_state := r_ar
                rd_addr := in.bits.addr
                rd_size := in.bits.mem_size
                rd_signed := in.bits.mem_signed
                rd_pc := in.bits.pc
            }
        }
        is(r_ar){
            when(AXI4_port.arready){
                r_state := r_data
            }
        }
        is(r_data){
            when(AXI4_port.rvalid){
                // rdata 是单拍脉冲，这拍就要交给 EXU
                r_state := r_idle
            }
        }
    }

    switch(w_state){
        is(w_idle){
            when(in.fire && to_store){
                w_state := w_aw
                wr_addr := in.bits.addr
                wr_data := in.bits.wdata
                wr_size := in.bits.mem_size
                wr_pc := in.bits.pc
                aw_sent := false.B
                w_sent := false.B
            }
        }
        is(w_aw){
            when(!aw_sent){
                when(AXI4_port.awready){
                    aw_sent := true.B
                }
            }
            when(!w_sent){
                when(AXI4_port.wready){
                    w_sent := true.B
                }
            }
            when(store_done){
                // 地址/数据都已交给写通道 → 这拍就放行流水线，B 在后台等
                w_state := w_b
                aw_sent := false.B
                w_sent := false.B
                mtrace_u.io.valid := true.B
                mtrace_u.io.is_write := true.B
                mtrace_u.io.size := mtrace_size
                mtrace_u.io.data := wr_data
            }
        }
        is(w_b){
            when(AXI4_port.bvalid){
                w_state := w_idle
            }
        }
    }

    // 完成拍即报告拍（load 拿数据 / store 交完 AXI）
    when((r_state === r_data && AXI4_port.rvalid) || (w_state === w_aw && store_done)){
        out.valid := true.B
    }

    PerfCounter("LSU_get_data") := (w_state === w_b && AXI4_port.bvalid) ||
                                   (r_state === r_data && AXI4_port.rvalid)
    PerfCounter("LSU_load_get_data")  := (r_state === r_data && AXI4_port.rvalid)
    PerfCounter("LSU_store_get_data") := (w_state === w_b && AXI4_port.bvalid)
}
