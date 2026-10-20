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

    // 事务描述符：EXU 在 in.fire 后的下一拍就可能覆盖 ex_mem_pending，
    // 而响应期还要 addr(1,0)/size/signed（扩展）和 pc（mtrace），所以必须自己留一份
    val lsu_in_reg = Reg(Flipped(new LSUBundle))

    val rdata = WireDefault(0.U(32.W))
    val shifted_rdata = AXI4_port.rdata >> (lsu_in_reg.addr(1,0) << 3)
    val shifted_wdata = lsu_in_reg.wdata << (lsu_in_reg.addr(1,0) << 3)
    val is_write = lsu_in_reg.mem_op === MemOp.store
    val wmask = WireDefault(0.U(4.W))
    dontTouch(wmask)
    dontTouch(rdata)

    val awsize = MuxLookup(lsu_in_reg.mem_size, 2.U)(Seq(
        MemSize.byte -> 0.U,
        MemSize.half -> 1.U))
    val mtrace_size = MuxLookup(lsu_in_reg.mem_size, 4.U)(Seq(
        MemSize.byte -> 1.U,
        MemSize.half -> 2.U))
    switch(lsu_in_reg.mem_size){
        is(MemSize.byte){ wmask := 1.U << lsu_in_reg.addr(1,0) }
        is(MemSize.half){ wmask := 3.U << lsu_in_reg.addr(1,0) }
        is(MemSize.word){ wmask := "b1111".U }
    }
    // load 数据扩展
    switch(lsu_in_reg.mem_size){
        is(MemSize.byte){ rdata := Mux(lsu_in_reg.mem_signed,
            Cat(Fill(24, shifted_rdata(7)), shifted_rdata(7,0)),
            Cat(Fill(24, 0.U), shifted_rdata(7,0))) }
        is(MemSize.half){ rdata := Mux(lsu_in_reg.mem_signed,
            Cat(Fill(16, shifted_rdata(15)), shifted_rdata(15,0)),
            Cat(Fill(16, 0.U), shifted_rdata(15,0))) }
        is(MemSize.word){ rdata := shifted_rdata }
    }

    val mtrace_u = Module(new mtrace)
    mtrace_u.io.valid := false.B
    List( mtrace_u.io.data, mtrace_u.io.is_write, mtrace_u.io.size).map(_ := 0.U)
    mtrace_u.io.addr := lsu_in_reg.addr
    mtrace_u.io.pc := lsu_in_reg.pc

    in.ready := false.B
    out.valid := false.B
    out.bits.rdata := rdata

    AXI4_port.initMaster()
    AXI4_port.araddr := lsu_in_reg.addr
    AXI4_port.awaddr := lsu_in_reg.addr
    AXI4_port.wdata := lsu_in_reg.wdata
    AXI4_port.wstrb := wmask

    // s_addr: 交地址（store 还要交数据）；s_data: 等读数据
    val s_idle :: s_addr :: s_data :: Nil = Enum(3)
    val state = RegInit(s_idle)

    // 写通道地址/数据各交一次（slave 可能不在同一拍收）
    val aw_sent = RegInit(false.B)
    val w_sent = RegInit(false.B)
    val store_done = (aw_sent || AXI4_port.awready) && (w_sent || AXI4_port.wready)

    // store 的 B 响应不再串在状态机里等：AW/W 握手完就放行流水线，B 用标志异步收。
    // 下一次访存在 in.ready 里被 !b_pending 挡住 → store→load 的顺序天然保住
    // （不能只保证 W 握手：数据进 SDRAM 颗粒要等 core 的写流水和 B）。
    val b_pending = RegInit(false.B)

    // 给 WBU：有 store 未落地时，fence.i 必须等（否则 icache 重填可能读到旧代码）
    val b_pending_out = IO(Output(Bool()))
    b_pending_out := b_pending

    PerfCounter("LSU_access_latency") := (state === s_addr || state === s_data)
    PerfCounter("LSU_wait_free") := (state === s_addr)
    PerfCounter("LSU_wait_resp") := (state === s_data)
    // LSU 占用拍数 = 流水线被访存阻塞的拍数（EXU 在 lsu_busy 期间冻结 ex_pending）
    PerfCounter("LSU_busy") := state =/= s_idle
    // 读写通道分开归账：load 占读通道，store 占写通道
    PerfCounter("LSU_load_latency")  := (state === s_addr || state === s_data) && !is_write
    PerfCounter("LSU_store_latency") := (state === s_addr || state === s_data) && is_write
    PerfCounter("LSU_load_wait_free")  := (state === s_addr) && !is_write
    PerfCounter("LSU_store_wait_free") := (state === s_addr) && is_write
    PerfCounter("LSU_load_wait_resp")  := (state === s_data)
    PerfCounter("LSU_store_wait_resp") := false.B
    // 完成拍必须被接收：EXU 在 lsu_busy 期间恒 ready，这里恒为 0
    PerfCounter("LSU_report_lost") := out.valid && !out.ready

    switch(state){
        is(s_idle){
            in.ready := !b_pending   // 有 store 未确认时下一次访存要等（保 store→load 顺序）
            when(in.fire){
                state := s_addr
                lsu_in_reg := in.bits
            }
        }
        is(s_addr){
            when(is_write){
                when(!aw_sent){
                    AXI4_port.awaddr := lsu_in_reg.addr
                    AXI4_port.awsize := awsize
                    AXI4_port.awvalid := true.B
                    when(AXI4_port.awready){
                        aw_sent := true.B
                    }
                }
                when(!w_sent){
                    AXI4_port.wdata := shifted_wdata
                    AXI4_port.wstrb := wmask
                    AXI4_port.wlast := true.B
                    AXI4_port.wvalid := true.B
                    when(AXI4_port.wready){
                        w_sent := true.B
                    }
                }
                when(store_done){
                    // 地址/数据都已交给写通道 → 这拍就放行，不再等 bvalid，也不再多占一拍报告
                    state := s_idle
                    b_pending := true.B
                    aw_sent := false.B
                    w_sent := false.B
                    mtrace_u.io.valid := true.B
                    mtrace_u.io.is_write := true.B
                    mtrace_u.io.size := mtrace_size
                    mtrace_u.io.data := lsu_in_reg.wdata
                }

            }.otherwise{
                AXI4_port.arsize := awsize
                AXI4_port.arvalid := true.B
                when(AXI4_port.arready){
                    state := s_data
                }

            }
        }
        is(s_data){
            AXI4_port.rready := true.B
            when(AXI4_port.rvalid){
                // rdata 是单拍脉冲，这拍就要交给 EXU（本模块没有输出数据寄存器）
                state := s_idle
                mtrace_u.io.valid := true.B
                mtrace_u.io.is_write := false.B
                mtrace_u.io.size := mtrace_size
                mtrace_u.io.data := rdata
            }
        }
    }

    // 完成拍即报告拍（省掉原来的 s_wait_fetch 那拍）
    when((state === s_addr && is_write && store_done) || (state === s_data && AXI4_port.rvalid)){
        out.valid := true.B
    }

    // B 响应：只要有 store 在途就一直准备接收；收到就清标志
    AXI4_port.bready := b_pending
    when(b_pending && AXI4_port.bvalid){
        b_pending := false.B
    }

    PerfCounter("LSU_get_data") := (b_pending && AXI4_port.bvalid && AXI4_port.bready) ||
                                   (!is_write && AXI4_port.rvalid && AXI4_port.rready)
    PerfCounter("LSU_load_get_data")  := (!is_write && AXI4_port.rvalid && AXI4_port.rready)
    PerfCounter("LSU_store_get_data") := (b_pending && AXI4_port.bvalid && AXI4_port.bready)
    // store 已放行流水线、在后台等 B 的拍数（以及其中真的挡住下一次访存的拍数）
    PerfCounter("LSU_store_b_pending") := b_pending
    PerfCounter("LSU_store_gate") := b_pending && in.valid && !in.ready
}
