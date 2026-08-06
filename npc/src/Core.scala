package npc

import chisel3._
import npc.AXI._
// import npc.Xbar.{Arbiter,Xbar}
import npc.device._
import npc.PMU._

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
    val perf_u = Module(new PerfCounterArray)
    perf_u.io.clock := clock
    perf_u.io.reset := reset
    perf_u.io.events := IFU_u.perf_events | IDU_u.perf_events | EXU_u.perf_events | LSU_u.perf_events | WBU_u.perf_events
    // val Arbiter_u = Module(new Arbiter(2))
    // val Xbar_u = Module(new Xbar(3,area))
    // // val MemUnit_u = Module(new MemUnit())
    // val uart_u = Module(new uart)
    val CLINT_u = Module(new ysyx_26030092_CLINT())
    val CLINT_AXI4Router_u = Module(new ysyx_26030092_CLINT_AXI4Router())
    val AXI4Arbiter_u = Module(new ysyx_26030092_AXI4Arbiter(2))
    io.slave.initSlave()
    
    IFU_u.in <> RFS_u.pc_out
    // io.pc := RFS_u.pc_out.bits.pc
    // io.inst := IFU_u.out.bits.inst

    IDU_u.in <> IFU_u.out

    EXU_u.idu_out <> IDU_u.out

    EXU_u.lsu_in <> LSU_u.in
    EXU_u.lsu_out <> LSU_u.out
    
    EXU_u.wbu_in <> WBU_u.in

    EXU_u.RFS_ReadPort <> RFS_u.RFS_ReadPort

    WBU_u.out <> RFS_u.wbu_in
    IFU_u.AXI4_port <> AXI4Arbiter_u.in(0)
    LSU_u.AXI4_port <> CLINT_AXI4Router_u.in
    CLINT_AXI4Router_u.toSoC <> AXI4Arbiter_u.in(1)
    CLINT_AXI4Router_u.toCLINT <> CLINT_u.AXI4_port

    io.master <> AXI4Arbiter_u.out
}