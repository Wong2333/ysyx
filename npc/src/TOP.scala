package npc

import chisel3._

class Top extends Module{

    val io = IO(new Bundle{
        val enable = Input(Bool())
        val out1 = Output(UInt(7.W))
        val out2 = Output(UInt(7.W))
    });
    val LFSR_u = Module(new LFSR8());
    LFSR_u.io.enable := io.enable;
    val HexToSevenSeg_u1 = Module(new HexToSevenSeg())
    io.out1 := HexToSevenSeg_u1.io.seg_out

    HexToSevenSeg_u2.io.hex_in := LFSR_u.io.out(3,0)
    val HexToSevenSeg_u2 = Module(new HexToSevenSeg())
    io.out2 := HexToSevenSeg_u2.io.seg_out
    HexToSevenSeg_u2.io.hex_in := LFSR_u.io.out(7,4)
}