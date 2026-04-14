package npc

import chisel3._
import chisel3.util._

class HexToSevenSeg extends Module {
  val io = IO(new Bundle {
    val hex_in = Input(UInt(4.W))
    val seg_out = Output(UInt(7.W)) // 输出 a, b, c, d, e, f, g
  })

  // 共阴极数码管真值表 (0 亮，1 灭) -> 题目要求显示十六进制 0-F
  //                    0     1     2     3     4     5     6     7     8     9     A     b     C     d     E     F
  val hexTable = VecInit(
    "b1111110".U, // 0 : A-F亮, G灭
    "b0110000".U, // 1 : B,C亮
    "b1101101".U, // 2 : A,B,D,E,G亮
    "b1111001".U, // 3 : A,B,C,D,G亮
    "b0110011".U, // 4 : B,C,F,G亮
    "b1011011".U, // 5 : A,C,D,F,G亮
    "b1011111".U, // 6 : A,C,D,E,F,G亮
    "b1110000".U, // 7 : A,B,C亮
    "b1111111".U, // 8 : 全亮
    "b1111011".U, // 9 : A,B,C,D,F,G亮
    "b1110111".U, // A (10) : A,B,C,E,F,G亮
    "b0011111".U, // b (11) : C,D,E,F,G亮
    "b1001110".U, // C (12) : A,D,E,F亮
    "b0111101".U, // d (13) : B,C,D,E,G亮
    "b1001111".U, // E (14) : A,D,E,F,G亮
    "b1000111".U  // F (15) : A,E,F,G亮
  )
  io.seg_out := hexTable(io.hex_in)
}