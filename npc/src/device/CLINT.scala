package npc.device

import chisel3._
import chisel3.util._
import npc.AXI.AXI4Bundle

class ysyx_26030092_CLINT extends Module{
    val AXI4_port = IO(Flipped(new AXI4Bundle))

    AXI4_port.initSlave()
    val mtime = RegInit(0.U(64.W))
    mtime := mtime + 1.U

    val out_reg = Reg(UInt(32.W))

    val s_idle :: s_busy :: Nil = Enum(2)
    val status = RegInit(s_idle)

    switch(status){
        is(s_idle){
            when(AXI4_port.arvalid){
                when(AXI4_port.araddr === (0x02000000L + 0XBFF8).U){
                    AXI4_port.arready := true.B
                    out_reg := mtime(31,0)
                    status := s_busy
                }
                when(AXI4_port.araddr === (0x02000000L + 0XBFF8 + 4).U){
                    AXI4_port.arready := true.B
                    out_reg := mtime(63,32)
                    status := s_busy
                }
            }
        }
        is(s_busy){
            AXI4_port.rdata := out_reg
            AXI4_port.rresp := false.B
            AXI4_port.rvalid := true.B
            when(AXI4_port.rready){
                // skip_difftest.io.skip := true.B
                status := s_idle
            }
        }
    }
}

class ysyx_26030092_CLINT_AXI4Router extends Module {

  val in = IO(Flipped(new AXI4Bundle))
  val toCLINT = IO(new AXI4Bundle)
  val toSoC = IO(new AXI4Bundle)

  // CLINT 地址空间定义 (标准地址: 0x02000000 - 0x0200BFFF)
  val CLINT_BASE = 0x02000000L
  val CLINT_SIZE = 0xC000L

  in.initSlave()
  toCLINT.initMaster()
  toSoC.initMaster()

  val r_idle :: r_busy :: Nil = Enum(2)
  val r_status = RegInit(r_idle)
  val r_sel_clint = RegInit(false.B)

  switch(r_status) {
    is(r_idle) {
      when(in.arvalid) {
        val isCLINT = (in.araddr >= CLINT_BASE.U) && (in.araddr < (CLINT_BASE + CLINT_SIZE).U)
        
        r_sel_clint := isCLINT
        r_status := r_busy

        when(isCLINT) {
          toCLINT.connectARFrom(in, true.B)
          toCLINT.connectRFrom(in, true.B)
        }.otherwise {
          toSoC.connectARFrom(in, true.B)
          toSoC.connectRFrom(in, true.B)
        }
      }
    }
    is(r_busy) {
      // 保持连接直到握手完成
      when(r_sel_clint) {
        toCLINT.connectARFrom(in, true.B)
        toCLINT.connectRFrom(in, true.B)
      }.otherwise {
        toSoC.connectARFrom(in, true.B)
        toSoC.connectRFrom(in, true.B)
      }

      when(in.rready && in.rvalid) {
        r_status := r_idle
      }
    }
  }

  // ================= 写通道状态机 =================
  val w_idle :: w_busy :: Nil = Enum(2)
  val w_status = RegInit(w_idle)
  val w_sel_clint = RegInit(false.B)

  switch(w_status) {
    is(w_idle) {
      when(in.awvalid) {
        val isCLINT = (in.awaddr >= CLINT_BASE.U) && (in.awaddr < (CLINT_BASE + CLINT_SIZE).U)

        w_sel_clint := isCLINT
        w_status := w_busy

        when(isCLINT) {
          toCLINT.connectAWFrom(in, true.B)
          toCLINT.connectWFrom(in, true.B)
          toCLINT.connectBFrom(in, true.B)
        }.otherwise {
          toSoC.connectAWFrom(in, true.B)
          toSoC.connectWFrom(in, true.B)
          toSoC.connectBFrom(in, true.B)
        }
      }
    }
    is(w_busy) {
      when(w_sel_clint) {
        toCLINT.connectAWFrom(in, true.B)
        toCLINT.connectWFrom(in, true.B)
        toCLINT.connectBFrom(in, true.B)
      }.otherwise {
        toSoC.connectAWFrom(in, true.B)
        toSoC.connectWFrom(in, true.B)
        toSoC.connectBFrom(in, true.B)
      }

      when(in.bready && in.bvalid) {
        w_status := w_idle
      }
    }
  }
}
