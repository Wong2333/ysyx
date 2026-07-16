// package npc.Xbar
// import chisel3._
// import chisel3.util._
// import npc.MemPort

// class Arbiter(n_in : Int) extends Module{
//     val in = IO(Vec(n_in,new MemPort))
//     val out = IO(Flipped(new MemPort))

//     val r_sel = Reg(UInt(log2Ceil(n_in).W))
//     val r_grant = WireDefault(0.U(log2Ceil(n_in).W))

//     val r_idle :: r_busy :: Nil = Enum(2)
//     val r_status = RegInit(r_idle)

//     for(i <- 0 until n_in){
//         in(i).AR.arready := false.B
//         in(i).AW.awready := false.B
//         in(i).R.rvalid := false.B
//         in(i).R.rdata := 0.U
//         in(i).R.rresp := false.B
//         in(i).W.wready := false.B
//         in(i).B.bresp := false.B
//         in(i).B.bvalid := false.B
//     }
//     out.AR.arvalid := false.B
//     out.AR.araddr := 0.U
//     out.AW.awaddr := 0.U
//     out.AW.awvalid := false.B
//     out.B.bready := false.B
//     out.R.rready := false.B
//     out.W.wdata := 0.U
//     out.W.wstrb := 0.U
//     out.W.wvalid := false.B
//     switch(r_status){
//         is(r_idle){
//             for(i <- 0 until n_in){
//                 //in(i).AR.arready := true.B
//                 when(in(i).AR.arvalid){
//                     r_grant := i.U
//                 }
//             }
//             when(in(r_grant).AR.arvalid){
//                 r_sel := r_grant
//                 r_status := r_busy
//             }
//         }
//         is(r_busy){
//             out.AR <> in(r_sel).AR
//             out.R <> in(r_sel).R

//             when(out.R.rready && out.R.rvalid){
//                 r_status := r_idle
//             }
//         }
//     }

//     val w_sel = Reg(UInt(log2Ceil(n_in).W))
//     val w_grant = WireDefault(0.U(log2Ceil(n_in).W))

//     val w_idle :: w_busy :: Nil = Enum(2)
//     val w_status = RegInit(w_idle)

//     switch(w_status){
//         is(w_idle){
//             for(i <- 0 until n_in){
//                 //in(i).AR.arready := true.B
//                 when(in(i).AW.awvalid){
//                     w_grant := i.U
//                 }
//             }
//             when(in(w_grant).AW.awvalid){
//                 w_sel := w_grant
//                 w_status := w_busy
//             }
//         }
//         is(w_busy){
//             out.AW <> in(w_sel).AW
//             out.W <> in(w_sel).W
//             out.B <> in(w_sel).B

//             when(out.B.bready && out.B.bvalid){
//                 w_status := w_idle
//             }
//         }
//     }   
// }