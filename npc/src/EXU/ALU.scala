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

object ALUContorl extends ChiselEnum{
    val none,
    add,
    sub,
    and,
    or,
    xor,
    lui,
    sll,srl,sra, //左移右移
    slt,sltu, //小于
    equ = Value
}

class ALU extends Module{

    val in = IO(Input(new Bundle {
        val ctl = ALUContorl()
        val src1 = UInt(32.W)
        val src2 = UInt(32.W)
    }))
    val out = IO(Output(UInt(32.W)))
    out := DontCare
    switch(in.ctl){
        is(ALUContorl.add){
            out := in.src1 + in.src2
        }
        is(ALUContorl.sub){
            out := in.src1 - in.src2
        }
        is(ALUContorl.sltu){
            out := in.src1 < in.src2
        }
        is(ALUContorl.slt){
            out := in.src1.asSInt < in.src2.asSInt
        }
        is(ALUContorl.and){
            out := in.src1 & in.src2
        }
        is(ALUContorl.xor){
            out := in.src1 ^ in.src2
        }
        is(ALUContorl.or){
            out := in.src1 | in.src2
        }
        is(ALUContorl.equ){
            out := in.src1 === in.src2
        }
        is(ALUContorl.lui){
            out := in.src2
        }
        is(ALUContorl.sll){
            out := in.src1 << in.src2(4,0)
        }
        is(ALUContorl.srl){
            out := in.src1 >> in.src2(4,0)
        }
        is(ALUContorl.sra){
            out := (in.src1.asSInt >> in.src2(4,0)).asUInt
        }
    }
}

