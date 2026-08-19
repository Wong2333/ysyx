package npc

import chisel3._
import chisel3.util._
import npc.PMU._

// 提交指令信息：PC / 是否访存 / 访存地址（沿流水线 EXU → WBU）
class CommitInfoBundle extends Bundle {
  val pc = Output(UInt(32.W))
  val mem = Output(Bool())
  val mem_addr = Output(UInt(32.W))
}

class WriteBackPortBundle extends Bundle{
    val RegWaddr = Output(UInt(5.W))
    val RegWdata = Output(UInt(32.W))
    val RegWrite = Output(Bool())

    val CSRAddr = Output(UInt(12.W))
    val CSRData = Output(UInt(32.W))
    val CSRWrite = Output(Bool())
    val isTrap = Output(Bool())
    val PC = Output(UInt(32.W))
    val Exception_Code = Output(UInt(32.W))

    val icache_flush = Output(Bool())
}

// WBU → IFU 的 PC 更新端口：commit 脉冲 + 跳转/trap 事件
class RedirectBundle extends Bundle {
  val is_trap = Output(Bool())
  val pc_next = Output(UInt(32.W))
}

class Commit extends ExtModule{
    val clock = FlatIO(Input(Clock()))
    val reset = FlatIO(Input(Bool()))
    val pc = FlatIO(Input(UInt(32.W)))
    val valid = FlatIO(Input(Bool()))
    val mem_commit = FlatIO(Input(Bool()))
    val mem_addr = FlatIO(Input(UInt(32.W)))
    addResource("/Commit.v")
}

class ysyx_26030092_WBU extends Module{
    val in = IO(Flipped(Decoupled(new WriteBackPortBundle)))

    val WriteRegPort = IO(Output(new WriteRegPortBundle))
    val CSRWritePort = IO(Output(new CSRWritePortBundle))
    val ExceptionPort = IO(Output(new ExceptionInfoBundle))
    val icache_flush = IO(Output(Bool()))
    // RAW 冒险检测信息：WB 级（写回拍）的写寄存器信息
    val wb_wb_info = IO(Output(new StageWriteInfoBundle))
    val wb_csr_info = IO(Output(new CSRWriteInfoBundle))

    val commit_info = IO(Input(new CommitInfoBundle))

    in.ready := true.B

    WriteRegPort := 0.U.asTypeOf(new WriteRegPortBundle)
    CSRWritePort := 0.U.asTypeOf(new CSRWritePortBundle)
    ExceptionPort := 0.U.asTypeOf(new ExceptionInfoBundle)
    icache_flush := false.B

    // 写回数据已在 EXU 的 mem_pending 锁存，这里 fire 拍直出（不再二次锁存）
    when(in.fire){
        WriteRegPort.waddr := in.bits.RegWaddr
        WriteRegPort.wdata := in.bits.RegWdata
        WriteRegPort.wen := in.bits.RegWrite

        CSRWritePort.CSRWAddr := in.bits.CSRAddr
        CSRWritePort.CSRWData := in.bits.CSRData
        CSRWritePort.CSRWrite := in.bits.CSRWrite

        ExceptionPort.isTrap := in.bits.isTrap
        ExceptionPort.PC := in.bits.PC
        ExceptionPort.Exception_Code := in.bits.Exception_Code

        icache_flush := in.bits.icache_flush
    }

    // RAW 冒险检测信息：写回窗口 = fire 拍（RF 在拍末落盘）
    wb_wb_info.wen   := in.fire && in.bits.RegWrite
    wb_wb_info.rd    := in.bits.RegWaddr
    wb_csr_info.wen  := in.fire && in.bits.CSRWrite
    wb_csr_info.addr := in.bits.CSRAddr
    PerfCounter("WBU_commit") := in.fire

    // commit 通知：fire 锁存，下一拍触发 DPI-C（供 difftest/trace 消费）
    val commit_u = Module(new Commit)
    commit_u.clock := clock
    commit_u.reset := reset
    commit_u.valid := false.B
    commit_u.pc := commit_info.pc
    commit_u.mem_commit := commit_info.mem
    commit_u.mem_addr := commit_info.mem_addr

    when(in.fire){
        commit_u.valid := true.B
    }
}
