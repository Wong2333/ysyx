package npc

import chisel3._
import chisel3.util._
import scala.util.Random


class PmemReadBlackBox extends ExtModule{
    val io = FlatIO(new Bundle{
        val raddr = Input(UInt(32.W))
        val rdata = Output(UInt(32.W))
        val valid = Input(Bool())
        val error = Output(Bool())
    })
    addResource("/PmemReadBlackBox.v")
}

class PmemWriteBlackBox extends ExtModule {
    val io = FlatIO(new Bundle {
        val waddr = Input(UInt(32.W))
        val wdata = Input(UInt(32.W))
        val wmask = Input(UInt(4.W))
        val valid = Input(Bool())
        val error = Output(Bool())
    })
    addResource("/PmemWriteBlackBox.v")
}

class MemPort extends Bundle{

    val AR = new Bundle {
        val araddr = Input(UInt(32.W))
        val arvalid = Input(Bool())
        val arready = Output(Bool())
    }

    val R = new Bundle {
        val rdata = Output(UInt(32.W))
        val rresp = Output(Bool())
        val rvalid = Output(Bool())
        val rready = Input(Bool())
    }

    val AW = new Bundle {
        val awaddr = Input(UInt(32.W))
        val awvalid = Input(Bool())
        val awready = Output(Bool())
    }

    val W = new Bundle {
        val wdata = Input(UInt(32.W))
        val wstrb = Input(UInt(4.W))
        val wvalid = Input(Bool())
        val wready = Output(Bool())
    }

    val B = new Bundle {
        val bresp = Output(Bool())
        val bvalid = Output(Bool())
        val bready = Input(Bool())
    }

    // val addr = Input(UInt(32.W))
    // val wdata = Input(UInt(32.W))
    // val wen = Input(Bool())
    // val wmask = Input(UInt(4.W))

    // val rdata = Output(UInt(32.W))
    // val error = Output(Bool())

    // val reqValid = Input(Bool())
    // val respReady = Input(Bool())
    // val reqReady = Output(Bool())
    // val respValid = Output(Bool())
}

class MemUnit extends Module{

    val port = IO(new MemPort)

    val read_cnt = RegInit(0.U(4.W))
    val write_cnt = RegInit(0.U(4.W))

    val raddr_reg = Reg(UInt(32.W))
    val read_error_reg = Reg(Bool())

    val waddr_reg = Reg(UInt(32.W))
    val wdata_reg = Reg(UInt(32.W))
    val wmask_reg = Reg(UInt(4.W))

    val rdata_reg = Reg(UInt(32.W))

    val write_error_reg = Reg(Bool())

    val LFSR  = random.LFSR(4)
    val read_latency = RegInit(LFSR)
    val write_latency = RegInit(LFSR)

    val pmem_read = Module(new PmemReadBlackBox())
    val pmem_write = Module(new PmemWriteBlackBox())

    pmem_write.io.waddr := waddr_reg
    pmem_write.io.wdata := wdata_reg
    pmem_write.io.wmask := wmask_reg
    pmem_write.io.valid := false.B

    pmem_read.io.raddr := raddr_reg
    pmem_read.io.valid := false.B


    port.AR.arready := false.B

    port.R.rresp := read_error_reg
    port.R.rvalid := false.B
    port.R.rdata := rdata_reg

    port.AW.awready := false.B
    port.W.wready := false.B

    port.B.bresp := write_error_reg
    port.B.bvalid := false.B

    val s_idle :: s_busy :: s_valid :: Nil = Enum(3)
    val s_read_idle :: s_read_busy :: s_read_valid :: Nil = Enum(3)
    val read_status = RegInit(s_read_idle)

    switch(read_status){
        is(s_read_idle){
            port.AR.arready := true.B
            when(port.AR.arvalid){
                raddr_reg := port.AR.araddr
                read_status := s_read_busy
                read_latency := LFSR
            }
        }
        is(s_read_busy){
            read_cnt := read_cnt + 1.U
            when(read_cnt === read_latency){
                read_status := s_read_valid
                pmem_read.io.valid := true.B
                rdata_reg := pmem_read.io.rdata
                read_error_reg := pmem_read.io.error
            }
        }
        is(s_read_valid){
            port.R.rvalid := true.B
            when(port.R.rready){
                read_status:= s_read_idle
            }
        }
    }
    val s_write_addr_idle :: s_write_addr_busy :: Nil = Enum(2)
    val write_addr_status = RegInit(s_write_addr_idle)

    when(write_addr_status === s_write_addr_idle){
        port.AW.awready := true.B
        when(port.AW.awvalid){
            waddr_reg := port.AW.awaddr
            write_addr_status := s_write_addr_busy
        }
    }

    val s_write_data_idle :: s_write_data_busy :: Nil = Enum(2)
    val write_data_status = RegInit(s_write_data_idle)

    when(write_data_status === s_write_data_idle){
        port.W.wready := true.B
        when(port.W.wvalid){
            wdata_reg := port.W.wdata
            wmask_reg := port.W.wstrb
            write_data_status := s_write_data_busy
            write_latency := LFSR
        }
    }
    val s_write_wait :: s_write_valid :: Nil = Enum(2)
    val write_status = RegInit(s_write_wait)

    switch(write_status){
        is(s_write_wait){
            when(write_addr_status === s_write_addr_busy && 
                write_data_status === s_write_data_busy){
                write_cnt := write_cnt + 1.U
                when(write_cnt === write_latency){
                    write_status := s_write_valid
                    pmem_write.io.valid := true.B
                    write_error_reg := pmem_write.io.error
                }
            }
        }
        is(s_write_valid){
            port.B.bvalid := true.B
            when(port.B.bready){
                write_status := s_write_wait
                write_addr_status := s_write_addr_idle
                write_data_status := s_write_data_idle
            }
        }
    }


}