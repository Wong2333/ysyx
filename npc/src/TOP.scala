package npc

import chisel3._

class Top extends Module{

    val io = IO(new Bundle{
        val enable = Input(Bool())
        val out = Output(UInt(7.W))
    });
    val LFSR_u = Module(new LFSR8());
    LFSR_u.io.enable := io.enable;
    val HexToSevenSeg_u = Module(new HexToSevenSeg())
    io.out := HexToSevenSeg_u.io.seg_out
    HexToSevenSeg_u.io.hex_in := LFSR_u.io.out
}