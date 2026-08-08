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

    val write_mem_ctl = Output(MemControl())
    val mem_waddr = Output(UInt(32.W))
    val mem_wdata = Output(UInt(32.W))
}

// WBU → IFU 的 PC 更新端口：commit 脉冲 + 跳转/trap 事件
class PCUpdatePortBundle extends Bundle {
  val valid   = Output(Bool()) 
  val pc_we   = Output(Bool())
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
    val PCUpdatePort = IO(Output(new PCUpdatePortBundle))
    val MemWrite = IO(Output(Decoupled(new LSUWritePort)))

    val commit_info = IO(Input(new CommitInfoBundle))

    val perf_events = IO(Output(Vec(PerfEvent.numEvents, Bool())))
    perf_events.foreach(_ := false.B)

    in.ready := MemWrite.ready
    MemWrite.valid := in.valid
    MemWrite.bits.addr := in.bits.mem_waddr
    MemWrite.bits.wdata := in.bits.mem_wdata
    MemWrite.bits.mem_ctl := in.bits.write_mem_ctl


    WriteRegPort := 0.U.asTypeOf(new WriteRegPortBundle)
    CSRWritePort := 0.U.asTypeOf(new CSRWritePortBundle)
    ExceptionPort := 0.U.asTypeOf(new ExceptionInfoBundle)
    PCUpdatePort := 0.U.asTypeOf(new PCUpdatePortBundle)
    

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

        PCUpdatePort.valid := true.B
        PCUpdatePort.pc_we := in.bits.PCWrite || in.bits.isTrap
        PCUpdatePort.is_trap := in.bits.isTrap
        PCUpdatePort.pc_next := in.bits.PCNext

        perf_events(PerfEvent.WBU_commit_instruction) := true.B
    }

    // commit 通知：fire 锁存，下一拍触发 DPI-C（供 difftest/trace 消费）
    val commit_reg = RegInit(false.B)
    val commit_pc_reg = Reg(UInt(32.W))
    val commit_mem_reg = RegInit(false.B)
    val commit_mem_addr_reg = Reg(UInt(32.W))
    val commit_u = Module(new Commit)
    commit_u.clock := clock
    commit_u.reset := reset
    commit_u.valid := false.B
    commit_u.pc := DontCare
    commit_u.mem_commit := commit_mem_reg
    commit_u.mem_addr := commit_mem_addr_reg
    when(in.fire){
        commit_reg := true.B
        commit_pc_reg := commit_info.pc
        commit_mem_reg := commit_info.mem
        commit_mem_addr_reg := commit_info.mem_addr
    }
    when(commit_reg){
        commit_u.valid := true.B
        commit_reg := false.B
        commit_mem_reg := false.B
        commit_u.pc := commit_pc_reg
    }
}
