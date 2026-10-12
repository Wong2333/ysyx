package npc

import chisel3._
import npc.AXI._
// import npc.Xbar.{Arbiter,Xbar}
import npc.device._
import npc.PMU._
import npc.BPU._

class ysyx_26030092 extends Module{

    val area = Seq((0X2000000L, 0X200BFFFL),
                    (0x80000000L, 0x8fffffffL))

    val io = IO(new Bundle{
        // val pc = Output(UInt(32.W))
        // val inst = Output(UInt(32.W))
        // val WriteBack = Output(Bool())
        val interrupt = Input(Bool())
        val master = new AXI4Bundle()
        val slave = Flipped(new AXI4Bundle())
    })
    val IFU_u = Module(new ysyx_26030092_IFU())
    val RFS_u = Module(new ysyx_26030092_RFS())
    val LSU_u = Module(new ysyx_26030092_LSU())
    val IDU_u = Module(new ysyx_26030092_IDU())
    val EXU_u = Module(new ysyx_26030092_EXU())
    val WBU_u = Module(new ysyx_26030092_WBU())
    val BPU_u = Module(new BPU(4))
    // commit 数据通路：EXU → WBU（commit 通知在 WBU 内部）
    WBU_u.commit_info := EXU_u.commit_info

    // val Arbiter_u = Module(new Arbiter(2))
    // val Xbar_u = Module(new Xbar(3,area))
    // // val MemUnit_u = Module(new MemUnit())
    // val uart_u = Module(new uart)
    val CLINT_u = Module(new ysyx_26030092_CLINT())
    val CLINT_AXI4Router_u = Module(new ysyx_26030092_CLINT_AXI4Router())
    val AXI4Arbiter_u = Module(new ysyx_26030092_AXI4Arbiter(2))
    io.slave.initSlave()
    
    BPU_u.pc := IFU_u.bpu_pc
    IFU_u.bpu_predict := BPU_u.predict_port
    BPU_u.train_port := EXU_u.bpu_train


    IFU_u.flush_icache := WBU_u.icache_flush
    WBU_u.lsu_b_pending := LSU_u.b_pending_out
    IFU_u.flush := EXU_u.flush
    IFU_u.RedirectPC :=EXU_u.redirectPC

    IDU_u.in <> IFU_u.out
    IDU_u.RFS_ReadPort <> RFS_u.RFS_ReadPort
    IDU_u.flush := EXU_u.flush

    // RAW 冒险检测：各阶段写寄存器信息 → IDU
    IDU_u.ex_wb_info <> EXU_u.ex_wb_info
    IDU_u.mem_wb_info <> EXU_u.mem_wb_info
    IDU_u.ex_csr_info <> EXU_u.ex_csr_info
    IDU_u.mem_csr_info <> EXU_u.mem_csr_info

    EXU_u.idu_out <> IDU_u.out

    EXU_u.lsu_in <> LSU_u.in
    EXU_u.lsu_out <> LSU_u.out

    EXU_u.wbu_in <> WBU_u.in
    EXU_u.mtvec := RFS_u.mtvec_out

    RFS_u.WriteRegPort := WBU_u.WriteRegPort
    RFS_u.CSRWritePort := WBU_u.CSRWritePort
    RFS_u.ExceptionPort := WBU_u.ExceptionPort
    IFU_u.AXI4_port <> AXI4Arbiter_u.in(0)
    LSU_u.AXI4_port <> CLINT_AXI4Router_u.in
    CLINT_AXI4Router_u.toSoC <> AXI4Arbiter_u.in(1)
    CLINT_AXI4Router_u.toCLINT <> CLINT_u.AXI4_port

    io.master <> AXI4Arbiter_u.out

    PerfCounter("total_cycles") := true.B
}