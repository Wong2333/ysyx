package npc

import chisel3._
import firtoolresolver.shaded.org.fusesource.jansi.internal.Kernel32.MOUSE_EVENT_RECORD
import scala.reflect.internal.Mode

class Top extends Module{

    val io = IO(new Bundle{
        val rst = Input(Bool())
        val pc = Output(UInt(32.W))
        val inst = Output(UInt(32.W))
    })
    val IFU_u = Module(new IFU())
    val RFS_u = Module(new RFS())
    val LSU_u = Module(new LSU())
    val IDU_u = Module(new IDU())
    val EXU_u = Module(new EXU())
    val WBU_u = Module(new WBU())
    
    IFU_u.in.pc := RFS_u.pc
    io.pc := RFS_u.pc
    io.inst := IFU_u.out.inst

    IDU_u.in.pc := RFS_u.pc
    IDU_u.in.inst := IFU_u.out.inst

    EXU_u.in.DecodeBundle <> IDU_u.out.DecodeBundle
    EXU_u.in.EXU_CTL <> IDU_u.out.EXU_CTL

    EXU_u.MemPort <> LSU_u.in
    EXU_u.MemReadData <> LSU_u.out
    EXU_u.WriteBackPort <> WBU_u.in

    EXU_u.RFS_ReadPort <> RFS_u.RFS_ReadPort

    WBU_u.out <> RFS_u.WBU_in
}