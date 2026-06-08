package npc

import chisel3._
import chisel3.util._
import os.stat
import javax.print.DocFlavor.READER



class MemReadPort extends Bundle{
    val raddr = Input(UInt(32.W))
    val rdata = Output(UInt(32.W))
    val reqValid = Input(Bool())
    val respReady = Input(Bool())
    val reqReady = Output(Bool())
    val respValid = Output(Bool())
}

class MemReadUnit extends Module{
    val ReadPort = IO(new MemReadPort)

    val s_idle :: s_busy :: s_valid :: Nil = Enum(3)

    val status = RegInit(s_idle)

    val cnt = RegInit(0.U(5.W))
    val addr_reg = Reg(UInt(32.W))
    val data_reg = Reg(UInt(32.W))

    val latency = 10.U

    val pmem_read = new PmemReadBlackBox

    pmem_read.io.raddr := ReadPort.raddr
    ReadPort.rdata := "hFFFFFFFF".U

    switch(status){
        is(s_idle){
            ReadPort.reqReady := true.B
            ReadPort.respValid := false.B
            when(ReadPort.reqValid){
                addr_reg := ReadPort.raddr
                status := s_busy
            }
        }
        is(s_busy){
            ReadPort.reqReady := false.B
            ReadPort.respValid := false.B
            cnt := cnt + 1.U
            when(cnt === latency){
                status := s_valid
                data_reg := pmem_read.io.rdata
            }
        }
        is(s_valid){
            ReadPort.reqReady := false.B
            ReadPort.respValid := true.B
            ReadPort.rdata := data_reg
            when(ReadPort.respReady){
                status:= s_idle
            }
        }
    }

}