package npc.AXI
import chisel3._
import chisel3.util._
import npc.AXI.AXI4Bundle
import npc.PMU._

// 读写两条通道完全独立，并且**握手即松手**：
//   1) AR / AW+W 一握手，仲裁器立刻断开该通道（不再 hold 到 rlast / B）；
//   2) R / B 响应按 **ID 路由**回发起方（arid/awid 由仲裁器按被选中的 master 打上）；
//   3) 只要 SoC 侧支持多 outstanding，把 req/busy 改成队列即可 —— 本模块的路由不依赖
//      "当前借给谁"，所以天然可移植。
class ysyx_26030092_AXI4Arbiter(n_in : Int) extends Module{
    val in = IO(Vec(n_in, Flipped(new AXI4Bundle)))   // 接收多个 Master 的请求
    val out = IO(new AXI4Bundle)    // 输出给单个 Slave

    for(i <- 0 until n_in){
        in(i).initSlave()
    }
    out.initMaster()

    val sel_bits = log2Ceil(n_in)
    def routeR(src: AXI4Bundle): Unit = {
        // R 通道：Slave -> 按 rid 找到的 Master
        src.rready := MuxCase(false.B, (0 until n_in).map(i => (src.rid === i.U) -> in(i).rready))
        for(i <- 0 until n_in){
            in(i).rvalid := src.rvalid && (src.rid === i.U)
            in(i).rid    := src.rid
            in(i).rdata  := src.rdata
            in(i).rresp  := src.rresp
            in(i).rlast  := src.rlast
        }
    }
    def routeB(src: AXI4Bundle): Unit = {
        src.bready := MuxCase(false.B, (0 until n_in).map(i => (src.bid === i.U) -> in(i).bready))
        for(i <- 0 until n_in){
            in(i).bvalid := src.bvalid && (src.bid === i.U)
            in(i).bid    := src.bid
            in(i).bresp  := src.bresp
        }
    }

    // ================= 读通道 (AR / R) =================
    val r_idle :: r_ar :: r_busy :: Nil = Enum(3)
    val r_state = RegInit(r_idle)
    val r_sel   = RegInit(0.U(sel_bits.W))

    switch(r_state){
        is(r_idle){
            val ar_reqs = VecInit(in.map(_.arvalid)).asUInt
            when(ar_reqs =/= 0.U){
                r_sel := PriorityEncoder(ar_reqs)
                r_state := r_ar
            }
        }
        is(r_ar){
            // 只在这几拍接通 AR；握手后立刻松手
            out.connectARFrom(in(r_sel), true.B)
            out.arid := r_sel
            when(out.arvalid && out.arready){
                r_state := r_busy
            }
        }
        is(r_busy){
            routeR(out)
            when(out.rvalid && out.rready && out.rlast){
                r_state := r_idle
                when(out.rresp =/= 0.U){
                    printf("Access Fault!\n")
                    assert(false.B)
                }
            }
        }
    }

    // ================= 写通道 (AW / W / B) =================
    val w_idle :: w_hs :: w_busy :: Nil = Enum(3)
    val w_state = RegInit(w_idle)
    val w_sel   = RegInit(0.U(sel_bits.W))
    val aw_done = RegInit(false.B)
    val w_done  = RegInit(false.B)

    switch(w_state){
        is(w_idle){
            val aw_reqs = VecInit(in.map(_.awvalid)).asUInt
            when(aw_reqs =/= 0.U){
                w_sel := PriorityEncoder(aw_reqs)
                aw_done := false.B
                w_done  := false.B
                w_state := w_hs
            }
        }
        is(w_hs){
            // 交出 AW / W，两个都握完就松手
            out.connectAWFrom(in(w_sel), true.B)
            out.connectWFrom(in(w_sel), true.B)
            out.awid := w_sel
            when(out.awvalid && out.awready){ aw_done := true.B }
            when(out.wvalid  && out.wready && out.wlast){ w_done := true.B }
            when((aw_done || (out.awvalid && out.awready)) &&
                 (w_done  || (out.wvalid && out.wready && out.wlast))){
                w_state := w_busy
            }
        }
        is(w_busy){
            routeB(out)
            when(out.bvalid && out.bready){
                w_state := w_idle
                when(out.bresp =/= 0.U){
                    printf("Access Fault!\n")
                    assert(false.B)
                }
            }
        }
    }

    // AXI 通道占用：判断流水线是否已撞到"单通道带宽天花板"的总开关
    // 0 = IFU（icache refill），1 = LSU（load/store）
    PerfCounter("AXI_read_busy")  := (r_state === r_busy)
    PerfCounter("AXI_write_busy") := (w_state === w_busy)
    for(i <- 0 until n_in){
        PerfCounter(s"AXI_read_grant_$i")  := (r_state === r_busy) && (out.rid === i.U)
        PerfCounter(s"AXI_write_grant_$i") := (w_state === w_busy) && (out.bid === i.U)
    }
}
