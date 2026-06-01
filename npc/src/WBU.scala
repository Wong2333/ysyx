package npc

import chisel3._
import chisel3.util._

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

class WBU extends Module{
    val in = IO(Flipped(new WriteBackPortBundle))

    val out = IO(new Bundle {
        val WriteRegPort = Output(new WriteRegPortBundle)
        val CSRWritePort = new CSRWritePortBundle
        val ExceptionPort = new ExceptionInfoBundle
    })

    out.WriteRegPort.waddr := in.RegWaddr
    out.WriteRegPort.wdata := in.RegWdata
    out.WriteRegPort.wen := in.RegWrite
    out.WriteRegPort.PCWrite := in.PCWrite
    out.WriteRegPort.PCNext := in.PCNext

    out.CSRPort.CSRWAddr := in.CSRAddr
    out.CSRPort.CSRWData := in.CSRData
    out.CSRPort.CSRWrite := in.CSRWrite

    out.ExceptionPort.isTrap := in.isTrap
    out.ExceptionPort.PC := in.PC
    out.ExceptionPort.Exception_Code := in.Exception_Code
 
}