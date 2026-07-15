package npc

import chisel3._
import npc.Xbar.{Arbiter,Xbar}
import npc.device._

class Top extends Module{

    val area = Seq((0x10000000L, 0x10000002L),
                    (0x10000002L, 0x10000040L),
                    (0x80000000L, 0x8fffffffL))

    val io = IO(new Bundle{
        val pc = Output(UInt(32.W))
        val inst = Output(UInt(32.W))
        val WriteBack = Output(Bool())
    })
    val IFU_u = Module(new IFU())
    val RFS_u = Module(new RFS())
    val LSU_u = Module(new LSU())
    val IDU_u = Module(new IDU())
    val EXU_u = Module(new EXU())
    val WBU_u = Module(new WBU())
    val Arbiter_u = Module(new Arbiter(2))
    val Xbar_u = Module(new Xbar(3,area))
    val MemUnit_u = Module(new MemUnit())
    val uart_u = Module(new uart)
    val CLINT_u = Module(new CLINT)
    
    IFU_u.in <> RFS_u.pc_out
    io.pc := RFS_u.pc_out.bits.pc
    io.inst := IFU_u.out.bits.inst

    IDU_u.in <> IFU_u.out

    EXU_u.idu_out <> IDU_u.out

    EXU_u.lsu_in <> LSU_u.in
    EXU_u.lsu_out <> LSU_u.out
    
    EXU_u.wbu_in <> WBU_u.in

    EXU_u.RFS_ReadPort <> RFS_u.RFS_ReadPort

    WBU_u.out <> RFS_u.wbu_in

    io.WriteBack := RFS_u.wbu_in.fire

    IFU_u.mem_port <> Arbiter_u.in(0)
    LSU_u.mem_port <> Arbiter_u.in(1)
    Arbiter_u.out <> Xbar_u.in
    Xbar_u.out(0) <>  uart_u.port
    Xbar_u.out(1) <> CLINT_u.port
    Xbar_u.out(2) <> MemUnit_u.port

}