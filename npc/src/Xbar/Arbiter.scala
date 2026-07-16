package npc.Xbar
import chisel3._
import chisel3.util._
import npc.AXI.AXI4Bundle

class Arbiter(n_in : Int) extends Module{
    val in = IO(Vec(n_in,new AXI4Bundle))
    val out = IO(Flipped(new AXI4Bundle))

    val r_sel = Reg(UInt(log2Ceil(n_in).W))
    val r_grant = WireDefault(0.U(log2Ceil(n_in).W))

    val r_idle :: r_busy :: Nil = Enum(2)
    val r_status = RegInit(r_idle)

    for(i <- 0 until n_in){
        in(i).initSlave()
    }
    out.initMaster()
    switch(r_status){
        is(r_idle){
            for(i <- 0 until n_in){
                //in(i).AR.arready := true.B
                when(in(i).arvalid){
                    r_grant := i.U
                }
            }
            when(in(r_grant).arvalid){
                r_sel := r_grant
                r_status := r_busy
            }
        }
        is(r_busy){
            out.AR <> in(r_sel).AR
            out.R <> in(r_sel).R

            when(out.R.rready && out.R.rvalid){
                r_status := r_idle
            }
        }
    }

    val w_sel = Reg(UInt(log2Ceil(n_in).W))
    val w_grant = WireDefault(0.U(log2Ceil(n_in).W))

    val w_idle :: w_busy :: Nil = Enum(2)
    val w_status = RegInit(w_idle)

    switch(w_status){
        is(w_idle){
            for(i <- 0 until n_in){
                //in(i).AR.arready := true.B
                when(in(i).AW.awvalid){
                    w_grant := i.U
                }
            }
            when(in(w_grant).AW.awvalid){
                w_sel := w_grant
                w_status := w_busy
            }
        }
        is(w_busy){
            out.AW <> in(w_sel).AW
            out.W <> in(w_sel).W
            out.B <> in(w_sel).B

            when(out.B.bready && out.B.bvalid){
                w_status := w_idle
            }
        }
    }   
}