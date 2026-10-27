package npc.BPU

import chisel3._
import chisel3.util._

class BTBEntry(tag_bits :Int) extends Bundle{
    val tag = UInt(tag_bits.W)
    val target = UInt(30.W)
    val counter = UInt(2.W) // 2-bit 饱和计数器
    val jal = Bool()
    val valid = Bool()
}

class BPUTrainBundle extends Bundle{
    val valid = Bool()
    val taken = Bool()
    val pc = UInt(30.W)      // word 地址
    val target = UInt(30.W)  // word 地址
    val is_jal = Bool()
}

class BPUPredictBundle extends Bundle{
    val next_pc = UInt(32.W)
    val take = Bool()
    val valid = Bool()
}

// 4 路全相联 BTB：4 个 tag 并行比较，替换用 tree-PLRU。
// tag 只取 word 地址低 tag_bits 位：BPU 只是提示（EXU 的 shadow PC 会验证），
// 短 tag 只会让远端分支互相冒名 → 多一次 flush，不会算错。
class BPU(ways :Int, tag_bits :Int) extends Module{
    require(ways == 4, "tree-PLRU 这里只实现了 4 路")

    val train_port = IO(Input(new BPUTrainBundle))
    val pc = IO(Input(UInt(30.W)))
    val predict_port = IO(Output(new BPUPredictBundle))

    val btb = Reg(Vec(ways, new BTBEntry(tag_bits)))
    // 4 路 tree-PLRU，3 位，每位指向"最久没用"的那一侧
    val plru = RegInit(VecInit(Seq.fill(3)(false.B)))
    // 注意：复位必须放在本模块所有 when 的最后（Chisel 后写覆盖先写）。
    // 否则 firtool 会合成 valid <= wr_en | (~reset & valid)：复位不是"清成常数"，
    // 一旦 wr_en 依赖的其它状态是 X（门级网表上电时就是这样），valid 永远出不了 X，
    // 四值网表仿真里 BPU 就一直是 X，PC 也跟着变 X。
    // 放在最后 → valid <= reset ? 0 : (wr_en | valid)，复位优先级最高，能自愈。

    val tag = pc(tag_bits - 1, 0)

    // ---- 预测：4 路并行比较 ----
    val hit = (0 until ways).map(i => btb(i).valid && btb(i).tag === tag)
    predict_port.valid := hit.reduce(_ || _)
    predict_port.take := Mux1H(hit, btb.map(e => e.jal || e.counter >= 2.U))
    predict_port.next_pc := Cat(Mux1H(hit, btb.map(_.target)), 0.U(2.W))

    // ---- 训练 ----
    val train_tag = train_port.pc(tag_bits - 1, 0)
    val train_hit = (0 until ways).map(i => btb(i).valid && btb(i).tag === train_tag)
    val train_any = train_hit.reduce(_ || _)

    // 替换：有无效项先用无效项，否则用 PLRU 指向的那一路
    val lru_way = Mux(plru(0), Mux(plru(2), 3.U, 2.U), Mux(plru(1), 1.U, 0.U))
    val invalid = (0 until ways).map(i => !btb(i).valid)
    val victim_way = Mux(invalid.reduce(_ || _), PriorityEncoder(invalid), lru_way)

    val wr_way = Mux(train_any, PriorityEncoder(train_hit), victim_way)
    val wr_en = train_port.valid && (train_port.taken || train_any)

    when(wr_en){
        val e = btb(wr_way)
        e.valid := true.B
        e.tag := train_tag
        when(train_port.taken){
            e.jal := train_port.is_jal
            e.target := train_port.target
            // 目标变了就退回"弱跳"，否则计数器 +1
            when(train_any && e.target === train_port.target){
                when(e.counter =/= 3.U){
                    e.counter := e.counter + 1.U
                }
            }.otherwise{
                e.counter := 2.U
            }
        }.otherwise{
            when(e.counter =/= 0.U){
                e.counter := e.counter - 1.U
            }
        }
        // 刚用过的这一路标成 MRU：PLRU 位指向另一侧
        when(wr_way(1)){
            plru(0) := false.B
            plru(2) := !wr_way(0)
        }.otherwise{
            plru(0) := true.B
            plru(1) := !wr_way(0)
        }
    }

    // 复位放在最后：优先级最高，合成出 reset ? 0 : ...，不依赖任何其它状态
    when(reset.asBool){
        btb.foreach(_.valid := false.B)
        plru.foreach(_ := false.B)
    }
}
