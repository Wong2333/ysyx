package npc.AXI

import chisel3._
import chisel3.util._
import npc.AXI.{AXI4Bundle,ysyx_26030092_AXI4Arbiter}

class ysyx_26030092_AXI4Xbar(n_out : Int, ranges: Seq[(Long, Long)]) extends Module{

    val in = IO(Flipped(new AXI4Bundle))
    val out = IO(Vec(n_out, new AXI4Bundle))

    val startVec = VecInit(ranges.map(_._1.U(32.W)))
    val endVec   = VecInit(ranges.map(_._2.U(32.W)))

    val r_idle :: r_busy :: Nil = Enum(2)
    val r_status = RegInit(r_idle)
    val r_sel = RegInit(0.U(log2Ceil(n_out).W))

    val out_of_bound = WireDefault(false.B)

    in.initSlave()
    
    for(i <- 0 until n_out){
        out(i).initMaster()
    }
    // 读 状态机
    switch(r_status){
        is(r_idle){
            when(in.arvalid){
                out_of_bound := true.B
                for(i <- 0 until n_out){
                    val is_hit = (in.araddr >= startVec(i)) && (in.araddr < endVec(i))
                    when(is_hit){
                        out_of_bound := false.B
                        r_status := r_busy
                        r_sel := i.U
                        out(i).connectARFrom(in,true.B)
                        out(i).connectRFrom(in,true.B)
                    }
                }
            }
        }
        is(r_busy){
            out(r_sel).connectARFrom(in,true.B)
            out(r_sel).connectRFrom(in,true.B)
            when(in.rready && in.rvalid && in.rlast){
                r_status := r_idle
            }
        }
    }

    val w_idle :: w_busy :: Nil = Enum(2)
    val w_status = RegInit(r_idle)
    val w_sel = RegInit(0.U(log2Ceil(n_out).W))
    // 写 状态机
    switch(w_status){
        is(w_idle){
            when(in.awvalid){
                for(i <- 0 until n_out){
                    val is_hit = (in.awaddr >= startVec(i) && in.awaddr < endVec(i))
                    when(is_hit){
                        w_status := w_busy
                        w_sel := i.U
                        out(i).connectAWFrom(in,true.B)
                        out(i).connectWFrom(in,true.B)
                        out(i).connectBFrom(in,true.B)
                    }
                }
            }
        }
        is(w_busy){
            out(w_sel).connectAWFrom(in,true.B)
            out(w_sel).connectWFrom(in,true.B)
            out(w_sel).connectBFrom(in,true.B)
            when(in.bready && in.bvalid){
                w_status := w_idle
            }
        }
    }
    
}
