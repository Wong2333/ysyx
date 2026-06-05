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
    "b0000001".U, // 0 : A-F亮, G灭
    "b1001111".U, // 1 : B,C亮
    "b0010010".U, // 2 : A,B,D,E,G亮
    "b0000110".U, // 3 : A,B,C,D,G亮
    "b1001100".U, // 4 : B,C,F,G亮
    "b0100100".U, // 5 : A,C,D,F,G亮
    "b0100000".U, // 6 : A,C,D,E,F,G亮
    "b0001111".U, // 7 : A,B,C亮
    "b0000000".U, // 8 : 全亮
    "b0000100".U, // 9 : A,B,C,D,F,G亮
    "b0001000".U, // A (10) : A,B,C,E,F,G亮
    "b1100000".U, // b (11) : C,D,E,F,G亮
    "b0110001".U, // C (12) : A,D,E,F亮
    "b1000010".U, // d (13) : B,C,D,E,G亮
    "b0110000".U, // E (14) : A,D,E,F,G亮
    "b0111000".U  // F (15) : A,E,F,G亮
  )

  io.seg_out := hexTable(io.hex_in)
}