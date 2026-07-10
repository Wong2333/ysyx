package npc

import chisel3._

class Top extends Module{

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
}