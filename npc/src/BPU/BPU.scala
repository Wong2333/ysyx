package npc.BPU

import chisel3._
import chisel3.util._

class BTBEntry(index_size :Int) extends Bundle{
    val tag = UInt((30 - index_size).W)
    val target = UInt(30.W)
    val counter = UInt(2.W)
    val jal = Bool()
    val valid = Bool()
}

class BPUTrainBundle extends Bundle{
    val valid = Bool()
    val pc = UInt(32.W)
    val taken = Bool()
    val target = UInt(32.W)
    val is_jal = Bool()
}

class BPUPredictBundle extends Bundle{
    val next_pc = UInt(32.W)
    val take = Bool()
    val valid = Bool()
}

class BPU(entry_num :Int) extends Module{

    val train_port = IO(Input(new BPUTrainBundle))
    val pc = IO(Input(UInt(32.W)))
    val predict_port = IO(Output(new BPUPredictBundle))

    val index_bits = log2Ceil(entry_num)
    val btb = Reg(Vec(entry_num, new BTBEntry(index_bits)))

    val index = pc(index_bits + 1,2)


}