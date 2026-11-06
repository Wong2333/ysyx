package npc.BPU

import chisel3._
import chisel3.util._

class BTBEntry(tag_bits :Int) extends Bundle{
    val tag = UInt(tag_bits.W)
    val target = UInt(30.W)
    val counter = UInt(2.W)
    val jal = Bool()
    val use = Bool()
    val valid = Bool()
}

class BPUTrainBundle extends Bundle{
    val valid = Bool()
    val taken = Bool()
    val pc = UInt(30.W)      
    val target = UInt(30.W) 
    val is_jal = Bool()
}

class BPUPredictBundle extends Bundle{
    val next_pc = UInt(32.W)
    val take = Bool()
    val valid = Bool()
}

class BPU(ways :Int, tag_bits :Int) extends Module{

    val train_port = IO(Input(new BPUTrainBundle))
    val pc = IO(Input(UInt(30.W)))
    val predict_port = IO(Output(new BPUPredictBundle))

    val btb = Reg(Vec(ways, new BTBEntry(tag_bits)))

    val tag = pc(tag_bits - 1, 0)

    // 预测
    val hit = (0 until ways).map(i => btb(i).valid && btb(i).tag === tag)
    predict_port.valid := hit.reduce(_ || _)
    predict_port.take := Mux1H(hit, btb.map(e => e.jal || e.counter >= 2.U))
    predict_port.next_pc := Cat(Mux1H(hit, btb.map(_.target)), 0.U(2.W))

    // 训练
    val train_port_s1 = RegNext(train_port)
    val train_tag = train_port_s1.pc(tag_bits-1, 0)
    val train_hit = (0 until ways).map(i => btb(i).valid && btb(i).tag === train_tag)
    val train_hit_any = train_hit.reduce(_ || _) && train_port_s1.valid

    val invalid = (0 until ways).map(i => !btb(i).valid)
    val unused  = (0 until ways).map(i => btb(i).valid && !btb(i).use)

    val victim_way = Mux(invalid.reduce(_ || _), PriorityEncoder(invalid),
                    Mux(unused.reduce(_ || _), PriorityEncoder(unused), 0.U))
    
    val wr_en  = train_port_s1.valid && (train_port_s1.taken || train_hit_any)
    val wr_way = Mux(train_hit_any, PriorityEncoder(train_hit), victim_way)


    when(wr_en) {
        val e = btb(wr_way)
        e.valid := true.B
        e.tag   := train_tag
        e.use   := true.B
        when(train_port_s1.taken) {
            e.jal    := train_port_s1.is_jal
            e.target := train_port_s1.target
            when(train_hit_any && e.target === train_port_s1.target) { // 重复命中+1
                when(e.counter =/= 3.U) { e.counter := e.counter + 1.U }
            }.otherwise {
                e.counter := 2.U
            }
        }.otherwise {
            when(e.counter =/= 0.U) { e.counter := e.counter - 1.U }
        }
    }

    val all_used = !invalid.reduce(_ || _) && !unused.reduce(_ || _)
    when(all_used) { // 每周期检查是否use全1, 如果全1就清0
        btb.foreach(_.use := false.B)
    }


    when(reset.asBool) {
        btb.foreach(_.valid := false.B)
        btb.foreach(_.use   := false.B)
    }
}
