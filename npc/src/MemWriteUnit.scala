package npc

import chisel3._
import chisel3.util._
import npc.ALUContorl.add

class PmemReadBlackBox extends ExtModule{
    val io = FlatIO(new Bundle{
        val raddr = Input(UInt(32.W))
        val rdata = Output(UInt(32.W))
        val valid = Input(Bool())
    })
    addResource("/PmemReadBlackBox.v")
}

class PmemWriteBlackBox extends ExtModule {
    val io = FlatIO(new Bundle {
        val waddr = Input(UInt(32.W))
        val wdata = Input(UInt(32.W))
        val wmask = Input(UInt(4.W))
        val valid = Input(Bool())
    })
    addResource("/PmemWriteBlackBox.v")
}

class MemPort extends Bundle{
    val addr = Input(UInt(32.W))
    val wdata = Input(UInt(32.W))
    val wen = Input(Bool())
    val wmask = Input(UInt(4.W))

    val rdata = Output(UInt(32.W))

    val reqValid = Input(Bool())
    val respReady = Input(Bool())
    val reqReady = Output(Bool())
    val respValid = Output(Bool())
}

class MemUnit extends Module{

    val port = IO(new MemPort)

    val s_idle :: s_busy :: s_valid :: Nil = Enum(3)

    val status = RegInit(s_idle)

    val cnt = RegInit(0.U(5.W))

    val addr_reg = Reg(UInt(32.W))
    val wdata_reg = Reg(UInt(32.W))
    val wmask_reg = Reg(UInt(4.W))
    val wen_reg = Reg(Bool())
    val rdata_reg = Reg(UInt(32.W))

    val latency = 10.U

    val pmem_read = new PmemReadBlackBox
    val pmem_write = new PmemWriteBlackBox

    pmem_write.io.waddr := DontCare
    pmem_write.io.wdata := DontCare
    pmem_write.io.wmask := DontCare
    pmem_write.io.valid := false.B

    pmem_read.io.raddr := DontCare
    pmem_read.io.valid := false.B

    port.rdata := "hFFFFFFFF".U
    port.reqReady := false.B
    port.respValid := false.B

    switch(status){
        is(s_idle){
            port.reqReady := true.B
            when(port.reqValid){
                addr_reg := port.addr
                wdata_reg := port.wdata
                wmask_reg := port.wmask
                wen_reg := port.wen
                status := s_busy
            }
        }
        is(s_busy){
            cnt := cnt + 1.U
            when(cnt === latency){
                status := s_valid
                when(wen_reg){
                    pmem_write.io.valid := true.B
                    pmem_write.io.waddr := addr_reg
                    pmem_write.io.wdata := wdata_reg
                    pmem_write.io.wmask := wmask_reg
                }.otherwise{
                    pmem_read.io.valid := true.B
                    rdata_reg := pmem_read.io.rdata
                }

            }
        }
        is(s_valid){
            port.respValid := true.B
            port.rdata := rdata_reg
            when(port.respReady){
                status:= s_idle
            }
        }
    }

}