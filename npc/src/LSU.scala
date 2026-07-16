package npc

import chisel3._
import chisel3.util._
import npc.AXI.AXI4Bundle

class LSUBundle extends Bundle{
    val mem_ctl = Output(MemControl())
    val wdata = Output(UInt(32.W))
    val addr = Output(UInt(32.W))
}

class ysyx_26030092_LSU extends Module{
    val in = IO(Flipped(Decoupled(new LSUBundle)))
    val out = IO(Decoupled(new Bundle {
        val rdata = Output(UInt(32.W))
        val error = Output(Bool())
    }))

    val AXI4_port = IO(new AXI4Bundle)
    
    val lsu_in_reg = Reg(Flipped(new LSUBundle))
    val lsu_out_reg = Reg(new Bundle {
        val rdata = Output(UInt(32.W))
        val error = Output(Bool())
    })

    val rdata = WireDefault(0.U(32.W))
    val shifted_rdata = AXI4_port.rdata >> (lsu_in_reg.addr(1,0) << 3)
    val shifted_wdata = lsu_in_reg.wdata << (lsu_in_reg.addr(1,0) << 3)
    val is_write = lsu_in_reg.mem_ctl.isOneOf(MemControl.sw, MemControl.sh, MemControl.sb)
    val wmask = WireDefault(0.U(4.W))
    dontTouch(wmask)
    dontTouch(rdata)

    out.bits.rdata := DontCare

    in.ready := false.B
    out.valid := false.B
    out.bits := lsu_out_reg

    AXI4_port.initMaster()
    AXI4_port.araddr := lsu_in_reg.addr
    AXI4_port.awaddr := lsu_in_reg.addr
    AXI4_port.wdata := lsu_in_reg.wdata
    AXI4_port.wstrb := wmask

    val s_idle :: s_wait_mem_free :: s_wait_mem_resp :: s_wait_fetch :: Nil = Enum(4)
    val status = RegInit(s_idle)

    val waddr_ready = RegInit(false.B)
    val wdata_ready = RegInit(false.B)

    switch(status){
        is(s_idle){
            in.ready := true.B
            when(in.fire){
                status := s_wait_mem_free
                lsu_in_reg := in.bits
            }
        }
        is(s_wait_mem_free){
            when(is_write){
                when(!waddr_ready){
                    AXI4_port.awaddr := lsu_in_reg.addr
                    AXI4_port.awvalid := true.B
                    when(AXI4_port.awready){
                        printf("[LSU] awaddr=%x\n",AXI4_port.awaddr)
                        waddr_ready := true.B
                    }
                }
                when(!wdata_ready){
                    AXI4_port.wdata := shifted_wdata
                    AXI4_port.wstrb := wmask
                    AXI4_port.wvalid := true.B
                    when(AXI4_port.wready){
                        printf("[LSU] wdata=%x\n",AXI4_port.wdata)
                        wdata_ready := true.B
                    }
                }
                when(waddr_ready && wdata_ready){
                    status := s_wait_mem_resp
                    waddr_ready := false.B
                    wdata_ready := false.B
                }

            }.otherwise{
                AXI4_port.arvalid := true.B
                when(AXI4_port.arready){
                    status := s_wait_mem_resp
                }

            }
        }
        is(s_wait_mem_resp){
            when(is_write){
                AXI4_port.bready := true.B
                when(AXI4_port.bvalid){
                    status := s_wait_fetch
                    lsu_out_reg.error := AXI4_port.bresp
                }
            }.otherwise{
                AXI4_port.rready := true.B                
                when(AXI4_port.rvalid){
                    status := s_wait_fetch
                    lsu_out_reg.rdata := rdata
                    lsu_out_reg.error := AXI4_port.rresp
                }

            }
        }
        is(s_wait_fetch){
            out.valid := true.B
            when(out.fire){
                status := s_idle
            }
        }
    }


    switch(lsu_in_reg.mem_ctl){
        is(MemControl.lw){
            rdata := AXI4_port.rdata
        }
        is(MemControl.lb){
            rdata := Cat(Fill(24, shifted_rdata(7)), shifted_rdata(7, 0))
        }
        is(MemControl.lbu){
            rdata := Cat(Fill(24,0.U),shifted_rdata(7, 0))
        }
        is(MemControl.lh){
            rdata := Cat(Fill(16, shifted_rdata(15)), shifted_rdata(15, 0))
        }
        is(MemControl.lhu){
            rdata := Cat(Fill(16, 0.U), shifted_rdata(15, 0))
        }
        is(MemControl.sw){
            wmask := "b1111".U;
            // pmem_unit.port.wdata := lsu_in_reg.wdata
        }
        is(MemControl.sb){
            wmask := 1.U << lsu_in_reg.addr(1,0)
            // pmem_unit.port.wdata := shifted_wdata
        }
        is(MemControl.sh){
            wmask := 3.U << lsu_in_reg.addr(1,0)
            // pmem_unit.port.wdata := shifted_wdata
        }
    }
}