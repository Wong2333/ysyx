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
  val hexTable = VecInit("b1000000".U, "b1111001".U, "b0100100".U, "b0110000".U, "b0011001".U, "b0010010".U, "b0000010".U, "b1111000".U, 
                         "b0000000".U, "b0010000".U, "b0001000".U, "b0000011".U, "b1000110".U, "b0100001".U, "b0000110".U, "b0001110".U)

  io.seg_out := hexTable(io.hex_in)
}