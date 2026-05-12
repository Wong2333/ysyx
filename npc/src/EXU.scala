package npc

import chisel3._
import chisel3.util._

object ALU_CTL extends ChiselEnum{
    val none = Value
    val src1AddImm = Value
    val src1AddSrc2 = Value
    val ukn = Value("b1111".U)
}

class EXU extends Module{
    val io = IO(new Bundle {
        val imm = Input(UInt(32.W))
        val src1 = Input(UInt(32.W))
        val src2 = Input(UInt(32.W))
        val ALU_ctl = Input(ALU_CTL())

        val out = Output(UInt(32.W))
    })
    io.out := 0.U
    switch(io.ALU_ctl){
        is(ALU_CTL.src1AddImm){
            io.out := io.src1 + io.imm
        }
        is(ALU_CTL.src1AddSrc2){
            io.out := io.src1 + io.src2
        }
    }
}