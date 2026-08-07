package npc

import chisel3._
import chisel3.util._
import npc.PMU._

// 提交指令信息：PC / 是否访存 / 访存地址（沿流水线 EXU → WBU → RFS）
class CommitInfoBundle extends Bundle {
  val pc = Output(UInt(32.W))
  val mem = Output(Bool())
  val mem_addr = Output(UInt(32.W))
}

class WriteBackPortBundle extends Bundle{
    val PCNext = Output(UInt(32.W))
    val PCWrite = Output(Bool())

    val RegWaddr = Output(UInt(5.W))
    val RegWdata = Output(UInt(32.W))
    val RegWrite = Output(Bool())

    val CSRAddr = Output(UInt(12.W))
    val CSRData = Output(UInt(32.W))
    val CSRWrite = Output(Bool())
    val isTrap = Output(Bool())
    val PC = Output(UInt(32.W))
    val Exception_Code = Output(UInt(32.W))
}

class ysyx_26030092_WBU extends Module{
    val in = IO(Flipped(Decoupled(new WriteBackPortBundle)))

    val out = IO(Decoupled(new Bundle {
        val WriteRegPort = Output(new WriteRegPortBundle)
        val CSRWritePort = new CSRWritePortBundle
        val ExceptionPort = new ExceptionInfoBundle
    }))

    val perf_events = IO(Output(Vec(PerfEvent.numEvents, Bool())))
    perf_events.foreach(_ := false.B)
    when(out.fire){
        perf_events(PerfEvent.WBU_commit_instruction) := true.B
    }

    // 提交指令信息转发（EXU → RFS）
    val commit_info = IO(Input(new CommitInfoBundle))
    val commit_info_out = IO(Output(new CommitInfoBundle))
    commit_info_out := commit_info

    out.valid := in.valid
    in.ready := out.ready

    out.bits.WriteRegPort.waddr := in.bits.RegWaddr
    out.bits.WriteRegPort.wdata := in.bits.RegWdata
    out.bits.WriteRegPort.wen := in.bits.RegWrite
    out.bits.WriteRegPort.PCWrite := in.bits.PCWrite
    out.bits.WriteRegPort.PCNext := in.bits.PCNext

    out.bits.CSRWritePort.CSRWAddr := in.bits.CSRAddr
    out.bits.CSRWritePort.CSRWData := in.bits.CSRData
    out.bits.CSRWritePort.CSRWrite := in.bits.CSRWrite

    out.bits.ExceptionPort.isTrap := in.bits.isTrap
    out.bits.ExceptionPort.PC := in.bits.PC
    out.bits.ExceptionPort.Exception_Code := in.bits.Exception_Code
 
}