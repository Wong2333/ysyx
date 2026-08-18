package npc.EXUs

import chisel3._
import chisel3.util._

object ALUSrc1 extends ChiselEnum{
    val rs1,
    pc = Value
}

object ALUSrc2 extends ChiselEnum{
    val rs2,
    imm,
    shamt,
    csr = Value
}

object ALUOp extends ChiselEnum{
    val add,
    sub,
    and,
    or,
    xor,
    lui,
    sll,srl,sra, //左移右移
    slt,sltu //小于
    = Value
}

class ysyx_26030092_ALU extends Module{

    val in = IO(Input(new Bundle {
        val ctl = ALUOp()
        val src1 = UInt(32.W)
        val src2 = UInt(32.W)
    }))
    val out = IO(Output(UInt(32.W)))
    out := DontCare
    switch(in.ctl){
        is(ALUOp.add){
            out := in.src1 + in.src2
        }
        is(ALUOp.sub){
            out := in.src1 - in.src2
        }
        is(ALUOp.sltu){
            out := in.src1 < in.src2
        }
        is(ALUOp.slt){
            out := in.src1.asSInt < in.src2.asSInt
        }
        is(ALUOp.and){
            out := in.src1 & in.src2
        }
        is(ALUOp.xor){
            out := in.src1 ^ in.src2
        }
        is(ALUOp.or){
            out := in.src1 | in.src2
        }
        is(ALUOp.lui){
            out := in.src2
        }
        is(ALUOp.sll){
            out := in.src1 << in.src2(4,0)
        }
        is(ALUOp.srl){
            out := in.src1 >> in.src2(4,0)
        }
        is(ALUOp.sra){
            out := (in.src1.asSInt >> in.src2(4,0)).asUInt
        }
    }
}

