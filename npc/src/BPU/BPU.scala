package npc.BPU

import chisel3._
import chisel3.util._

class BTBEntry(index_size :Int) extends Bundle{
    val tag = UInt((30 - index_size).W)
    val target = UInt(30.W)
    val counter = UInt(1.W)
    val jal = Bool()
    val valid = Bool()
}

class BPUTrainBundle extends Bundle{
    val valid = Bool()
    val pc = UInt(30.W)
    val target = UInt(30.W)
    val is_jal = Bool()
}

class BPUPredictBundle extends Bundle{
    val next_pc = UInt(32.W)
    val take = Bool()
    val valid = Bool()
}

class BPU(entry_num :Int) extends Module{

    val train_port = IO(Input(new BPUTrainBundle))
    val pc = IO(Input(UInt(30.W)))
    val predict_port = IO(Output(new BPUPredictBundle))

    val index_bits = log2Ceil(entry_num)
    val btb = Reg(Vec(entry_num, new BTBEntry(index_bits)))
    withReset(reset){
        btb.foreach(_.valid := false.B)
    }

    val index = pc(index_bits - 1,0)
    val tag = pc(29, index_bits)

    predict_port.valid := btb(index).tag === tag && btb(index).valid
    predict_port.take := btb(index).jal || btb(index).counter === 1.U
    predict_port.next_pc := Cat(btb(index).target ,0.U(2.W))

    when(train_port.valid){
        val train_index = train_port.pc(index_bits -1, 0)
        val train_tag = train_port.pc(29, index_bits)
        val train_hit = train_tag === btb(train_index).tag && btb(train_index).valid
        val train_entry = btb(train_index)
            train_entry.jal := train_port.is_jal
            train_entry.valid := true.B
            train_entry.tag := train_tag
            when(train_hit && train_entry.target === train_port.target){
                train_entry.counter := 1.U
            }.otherwise{
                train_entry.counter := 0.U
                train_entry.target := train_port.target
            }
    }

}