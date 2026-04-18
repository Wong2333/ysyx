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
    IFU_u.io.rst := io.rst
    IFU_u.io.pc_next := WBU_u.io.pc_next
    IFU_u.io.pc_we := IDU_u.io.pc_we

    IDU_u.io.rst := io.rst
    IDU_u.io.pc := IFU_u.io.pc
    IDU_u.io.inst := IFU_u.io.inst
    IDU_u.io.reg_rdata1 := RFS_u.io.rdata1
    IDU_u.io.reg_rdata2 := RFS_u.io.rdata2

    LSU_u.io.valid := IDU_u.io.mem_valid
    LSU_u.io.wen := IDU_u.io.mem_wen
    LSU_u.io.mem_ctl := IDU_u.io.mem_ctl
    LSU_u.io.src1 := IDU_u.io.src1
    LSU_u.io.src2 := IDU_u.io.src2
    LSU_u.io.ALU_out := EXU_u.io.ALU_ctl

    RFS_u.io.wdata := WBU_u.io.reg_wdata
    RFS_u.io.waddr := IDU_u.io.reg_waddr
    RFS_u.io.wen := IDU_u.io.reg_we
    RFS_u.io.raddr1 := IDU_u.io.rs1
    RFS_u.io.raddr2 := IDU_u.io.rs2

    WBU_u.io.pc := IFU_u.io.pc
    WBU_u.io.ALU_out := EXU_u.io.out
    WBU_u.io.imm := IDU_u.io.imm
    WBU_u.io.mem_rdata := LSU_u.io.mem_out
    WBU_u.io.WB_sel := IDU_u.io.WB_sel
    WBU_u.io.pc_sel := IDU_u.io.pc_sel

}