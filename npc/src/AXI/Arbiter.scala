package npc.AXI
import chisel3._
import chisel3.util._
import npc.AXI.AXI4Bundle
import npc.PMU._


class ysyx_26030092_AXI4Arbiter(n_in : Int) extends Module{
    val in = IO(Vec(n_in, Flipped(new AXI4Bundle)))   // 接收多个 Master 的请求
    val out = IO(new AXI4Bundle)    // 输出给单个 Slave

    for(i <- 0 until n_in){
        in(i).initSlave()
    }
    out.initMaster()

    // ================= 读通道仲裁 (AR / R) =================
    val r_idle :: r_busy :: Nil = Enum(2)
    val r_state = RegInit(r_idle)
    val r_sel = RegInit(0.U(log2Ceil(n_in).W))

    switch(r_state){
        is(r_idle){
            val ar_reqs = VecInit(in.map(_.arvalid)).asUInt
            when(ar_reqs =/= 0.U){
                r_sel := PriorityEncoder(ar_reqs)
                r_state := r_busy
            }
        }
        is(r_busy){
            // --- 接通 AR 通道 (Master -> Arbiter -> Slave) ---
            out.connectARFrom(in(r_sel), true.B)

            // --- 接通 R 通道 (Slave -> Arbiter -> Master) ---
            out.connectRFrom(in(r_sel), true.B)

            // 关键：只有当读数据最后一拍握手完成，才释放总线
            when(out.rvalid && out.rready && out.rlast){
                r_state := r_idle
                when(out.rresp =/= 0.U){
                    printf("Access Fault!\n")
                    assert(false.B)
                }
            }
        }
    }

    // ================= 写通道仲裁 (AW / W / B) =================
    val w_idle :: w_busy :: Nil = Enum(2)
    val w_state = RegInit(w_idle)
    val w_sel = RegInit(0.U(log2Ceil(n_in).W)) // 记录当前选中的 Master

    switch(w_state){
        is(w_idle){
            val aw_reqs = VecInit(in.map(_.awvalid)).asUInt
            when(aw_reqs =/= 0.U){
                w_sel := PriorityEncoder(aw_reqs)
                w_state := w_busy
            }
        }
        is(w_busy){
            // --- 接通 AW 通道 ---
            out.connectAWFrom(in(w_sel), true.B)

            // --- 接通 W 通道 ---
            out.connectWFrom(in(w_sel), true.B)

            // --- 接通 B 通道 ---
            out.connectBFrom(in(w_sel), true.B)

            // 关键：只有当写响应握手完成，才释放总线
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
    PerfCounter("AXI_read_busy") := r_state === r_busy
    PerfCounter("AXI_write_busy") := w_state === w_busy
    for(i <- 0 until n_in){
        PerfCounter(s"AXI_read_grant_$i") := (r_state === r_busy) && (r_sel === i.U)
        PerfCounter(s"AXI_write_grant_$i") := (w_state === w_busy) && (w_sel === i.U)
    }
}
