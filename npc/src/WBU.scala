package npc

import chisel3._
import chisel3.util._

class WriteBackPortBundle extends Bundle{
    val PCNext = Output(UInt(32.W))
    val JumpOrBranch = Output(Bool())
    val RegWaddr = Output(UInt(5.W))
    val RegWdata = Output(UInt(32.W))
    val RegWrite = Output(Bool())

    val CSRAddr = Output(UInt(12.W))
    val CSRData = Output(UInt(32.W))
    val CSRWrite = Output(Bool())
}

class WBU extends Module{
    val in = IO(Flipped(new WriteBackPortBundle))

    val out = IO(new Bundle {
        val IFUBundle = Output(new Bundle {
            val pc_next = Input(UInt(32.W))
            val pc_we = Input(Bool())
        })
        val RFSBundle = Output(new WriteRegPortBundle)

    })

    out.IFUBundle.pc_next := in.PCNext
    out.IFUBundle.pc_we := in.JumpOrBranch
    out.RFSBundle.waddr := in.RegWaddr
    out.RFSBundle.wdata := in.RegWdata
    out.RFSBundle.wen := in.RegWrite
    out.RFSBundle.CSRAddr := in.CSRAddr
    out.RFSBundle.CSRData := in.CSRData
    out.RFSBundle.CSRWrite := in.CSRWrite
 
}