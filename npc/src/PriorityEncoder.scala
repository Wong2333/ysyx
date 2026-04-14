package npc

import chisel3._
import chisel3.util.log2Ceil

class PriorityEncoder(n: Int) extends Module{

    require(n >= 2, "Input width must be at least 2")
    val io = IO(new Bundle {
        val in = Input(UInt(n.W));
        val out = Output(UInt(log2Ceil(n).W));
        val vaild = Output(Bool());
    });

    val outWire = WireDefault(0.U(log2Ceil(n).W));
    for(i <- 0 until n){
        when(io.in(i)){
            outWire := i.U;
        }
    }

    io.out := outWire;
    io.vaild := io.in.orR;
}