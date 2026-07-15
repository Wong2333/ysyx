package npc.Xbar

import chisel3._
import chisel3.util._
import npc.MemPort

class Xbar(n_out : Int, ranges: Seq[(Long, Long)]) extends Module{

    val in = IO(new MemPort)
    val out = IO(Vec(n_out, Flipped(new MemPort)))

    val startVec = VecInit(ranges.map(_._1.U(32.W)))
    val endVec   = VecInit(ranges.map(_._2.U(32.W)))

    val r_idle :: r_busy :: Nil = Enum(2)
    val r_status = RegInit(r_idle)
    val r_sel = RegInit(0.U(log2Ceil(n_out).W))

    val out_of_bound = WireDefault(false.B)

    in.AR.arready := false.B
    in.AW.awready := false.B
    in.B.bvalid := false.B
    in.B.bresp := false.B
    in.R.rdata := 0.U
    in.R.rresp := false.B
    in.R.rvalid := false.B
    in.W.wready := false.B
    
    for(i <- 0 until n_out){
        out(i).AR.arvalid := false.B
        out(i).AR.araddr := 0.U
        out(i).AW.awaddr := 0.U
        out(i).AW.awvalid := false.B
        out(i).B.bready := false.B
        out(i).R.rready := false.B
        out(i).W.wdata := 0.U
        out(i).W.wstrb := 0.U
        out(i).W.wvalid := false.B
    }
    // 读 状态机
    switch(r_status){
        is(r_idle){
            when(in.AR.arvalid){
                out_of_bound := true.B
                for(i <- 0 until n_out){
                    val is_hit = (in.AR.araddr >= startVec(i)) && (in.AR.araddr < endVec(i))
                    when(is_hit){
                        out_of_bound := false.B
                        r_status := r_busy
                        r_sel := i.U
                        in.AR <> out(i).AR
                        in.R <> out(i).R
                    }
                }
            }
        }
        is(r_busy){
            in.R <> out(r_sel).R
            in.AR <> out(r_sel).AR
            when(in.R.rready && in.R.rvalid){
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
            when(in.AW.awvalid){
                for(i <- 0 until n_out){
                    val is_hit = (in.AW.awaddr >= startVec(i) && in.AW.awaddr < endVec(i))
                    when(is_hit){
                        w_status := w_busy
                        w_sel := i.U
                        in.AW <> out(i).AW
                        in.W <> out(i).W
                        in.B <> out(i).B
                    }
                }
            }
        }
        is(w_busy){
            in.AW <> out(w_sel).AW
            in.W <> out(w_sel).W
            in.B <> out(w_sel).B
            when(in.B.bready && in.B.bvalid){
                w_status := w_idle
            }
        }
    }
    
}
