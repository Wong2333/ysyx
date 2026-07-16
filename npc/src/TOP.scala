package npc

import chisel3._
import npc.AXI.AXI4Bundle
import npc.AXI.AXI4Arbiter
// import npc.Xbar.{Arbiter,Xbar}
// import npc.device._

class ysyx_26030092 extends Module{

    val area = Seq((0x10000000L, 0x10000002L),
                    (0x10000002L, 0x10000040L),
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
    // val Arbiter_u = Module(new Arbiter(2))
    // val Xbar_u = Module(new Xbar(3,area))
    // // val MemUnit_u = Module(new MemUnit())
    // val uart_u = Module(new uart)
    // val CLINT_u = Module(new CLINT)
    val AXI4Arbiter_u = Module(new AXI4Arbiter(2))
    
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
    LSU_u.AXI4_port <> AXI4Arbiter_u.in(1)

    io.master <> AXI4Arbiter_u.out
    io.slave.initSlave()
    // io.WriteBack := RFS_u.wbu_in.fire

    // IFU_u.mem_port <> Arbiter_u.in(0)
    // LSU_u.mem_port <> Arbiter_u.in(1)
    // Arbiter_u.out <> Xbar_u.in
    // Xbar_u.out(0) <>  uart_u.port
    // Xbar_u.out(1) <> CLINT_u.port
    // Xbar_u.out(2) <> MemUnit_u.port

}