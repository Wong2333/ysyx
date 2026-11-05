package npc.AXI
import chisel3._
import chisel3.util._
import npc.AXI.AXI4Bundle
import npc.PMU._

class ysyx_26030092_AXI4Arbiter(n_in : Int) extends Module{
    val master = IO(Vec(n_in, Flipped(new AXI4Bundle)))
    val slave = IO(new AXI4Bundle)

    for(i <- 0 until n_in){
        master(i).initSlave()
    }
    slave.initMaster()

    val sel_bits = log2Ceil(n_in)
    def routeR(src: AXI4Bundle): Unit = {
        src.rready := MuxCase(false.B, (0 until n_in).map(i => (src.rid === i.U) -> master(i).rready))
        for(i <- 0 until n_in){
            master(i).rvalid := src.rvalid && (src.rid === i.U)
            master(i).rid    := src.rid
            master(i).rdata  := src.rdata
            master(i).rresp  := src.rresp
            master(i).rlast  := src.rlast
        }
    }
    def routeB(src: AXI4Bundle): Unit = {
        src.bready := MuxCase(false.B, (0 until n_in).map(i => (src.bid === i.U) -> master(i).bready))
        for(i <- 0 until n_in){
            master(i).bvalid := src.bvalid && (src.bid === i.U)
            master(i).bid    := src.bid
            master(i).bresp  := src.bresp
        }
    }

    // AR
    val arLock  = RegInit(false.B)
    val arLockSel = RegInit(0.U(sel_bits.W))
    val ar_req_bits = VecInit(master.map(_.arvalid)).asUInt
    val ar_sel  = Mux(arLock, arLockSel, PriorityEncoder(ar_req_bits))
    when(slave.arvalid && !slave.arready && !arLock) { // 锁存ar_sel
        arLock    := true.B
        arLockSel := ar_sel
    }
    when(slave.arvalid && slave.arready) {
        arLock := false.B
    }
    slave.connectARFrom(master(ar_sel), true.B)
    slave.arid := ar_sel

    // R
    for(i <- 0 until n_in){
        master(i).rvalid := slave.rvalid && (slave.rid === i.U)
        master(i).rid    := slave.rid
        master(i).rdata  := slave.rdata
        master(i).rresp  := slave.rresp
        master(i).rlast  := slave.rlast
    }
    when(slave.rvalid){
        slave.rready := master(slave.rid).rready
    }
    // R

    // switch(ar_state){
    //     is(ar_idle){
    //         val ar_reqs = VecInit(master.map(_.arvalid)).asUInt
    //         when(ar_reqs =/= 0.U){
    //             ar_sel := PriorityEncoder(ar_reqs)
    //             ar_state := ar_busy
    //         }
    //     }
    //     is(ar_busy){
    //         slave.connectARFrom(master(ar_sel), true.B)
    //         slave.arid := ar_sel
    //         when(slave.arvalid && slave.arready){
    //             ar_state := ar_busy
    //         }
    //     }
    //     is(r_busy){
    //         routeR(slave)
    //         when(slave.rvalid && slave.rready && slave.rlast){
    //             r_state := r_idle
    //             when(slave.rresp =/= 0.U){
    //                 printf("Access Fault!\n")
    //                 assert(false.B)
    //             }
    //         }
    //     }
    // }

    // AW & W
    // val w_idle :: w_hs :: w_busy :: Nil = Enum(3)
    // val w_state = RegInit(w_idle)
    // val w_sel   = RegInit(0.U(sel_bits.W))
    // val aw_done = RegInit(false.B)
    // val w_done  = RegInit(false.B)

    // switch(w_state){
    //     is(w_idle){
    //         val aw_reqs = VecInit(master.map(_.awvalid)).asUInt
    //         when(aw_reqs =/= 0.U){
    //             w_sel := PriorityEncoder(aw_reqs)
    //             aw_done := false.B
    //             w_done  := false.B
    //             w_state := w_hs
    //         }
    //     }
    //     is(w_hs){
    //         // 交出 AW / W，两个都握完就松手
    //         slave.connectAWFrom(master(w_sel), true.B)
    //         slave.connectWFrom(master(w_sel), true.B)
    //         slave.awid := w_sel
    //         when(slave.awvalid && slave.awready){ aw_done := true.B }
    //         when(slave.wvalid  && slave.wready && slave.wlast){ w_done := true.B }
    //         when((aw_done || (slave.awvalid && slave.awready)) &&
    //              (w_done  || (slave.wvalid && slave.wready && slave.wlast))){
    //             w_state := w_busy
    //         }
    //     }
    //     is(w_busy){
    //         routeB(slave)
    //         when(slave.bvalid && slave.bready){
    //             w_state := w_idle
    //             when(slave.bresp =/= 0.U){
    //                 printf("Access Fault!\n")
    //                 assert(false.B)
    //             }
    //         }
    //     }
    // }

    slave.connectAWFrom(master(1), true.B)
    slave.connectWFrom(master(1), true.B)
    master(1).connectBFrom(slave, true.B)

    // AXI 通道占用：判断流水线是否已撞到"单通道带宽天花板"的总开关
    // 0 = IFU（icache refill），1 = LSU（load/store）
    // PerfCounter("AXI_read_busy")  := (r_state === r_busy)
    // PerfCounter("AXI_write_busy") := (w_state === w_busy)
    // // 两个 master 同时抢读通道的拍数（判断轮转仲裁是否真的有用）
    // PerfCounter("AXI_read_contend") := VecInit(master.map(_.arvalid)).asUInt.andR
    // for(i <- 0 until n_in){
    //     PerfCounter(s"AXI_read_grant_$i")  := (r_state === r_busy) && (r_sel === i.U)
    //     PerfCounter(s"AXI_write_grant_$i") := (w_state === w_busy) && (w_sel === i.U)
    // }
}
