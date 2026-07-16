package npc.AXI

import chisel3._
import chisel3.util._

class AXI4Bundle extends Bundle {
    
    // ================= 写地址通道 (AW) =================
        val awready = Input(Bool())       // Slave 准备好接收写地址
        val awvalid = Output(Bool())      // Master 写地址有效
        val awaddr  = Output(UInt(32.W))  // 写地址 (32位)
        val awid    = Output(UInt(4.W))   // 写操作ID (4位)
        val awlen   = Output(UInt(8.W))   // 突发长度 (8位)
        val awsize  = Output(UInt(3.W))   // 突发大小 (3位)
        val awburst = Output(UInt(2.W))   // 突发类型 (2位)


    // ================= 写数据通道 (W) =================
    val wready = Input(Bool())        // Slave 准备好接收写数据
    val wvalid = Output(Bool())       // Master 写数据有效
    val wdata  = Output(UInt(32.W))   // 写数据 (32位)
    val wstrb  = Output(UInt(4.W))    // 字节选通信号 (4位, 每bit对应1Byte)
    val wlast  = Output(Bool())       // 标记本次突发传输的最后一拍数据

    // ================= 写响应通道 (B) =================
    val bready = Output(Bool())       // Master 准备好接收写响应
    val bvalid = Input(Bool())        // Slave 写响应有效
    val bresp  = Input(UInt(2.W))     // 写响应状态 (2位, 如 00=OKAY)
    val bid    = Input(UInt(4.W))     // 写响应ID (与awid对应)

    // ================= 读地址通道 (AR) =================
    val arready = Input(Bool())       // Slave 准备好接收读地址
    val arvalid = Output(Bool())      // Master 读地址有效
    val araddr  = Output(UInt(32.W))  // 读地址 (32位)
    val arid    = Output(UInt(4.W))   // 读操作ID (4位)
    val arlen   = Output(UInt(8.W))   // 突发长度 (8位)
    val arsize  = Output(UInt(3.W))   // 突发大小 (3位)
    val arburst = Output(UInt(2.W))   // 突发类型 (2位)

    // ================= 读数据通道 (R) =================
    val rready = Output(Bool())       // Master 准备好接收读数据
    val rvalid = Input(Bool())        // Slave 读数据有效
    val rresp  = Input(UInt(2.W))     // 读响应状态 (2位)
    val rdata  = Input(UInt(32.W))    // 读数据 (32位)
    val rid    = Input(UInt(4.W))     // 读响应ID (与arid对应)
    val rlast  = Input(Bool())        // 标记本次突发传输的最后一拍数据

    def initMaster(): Unit = {

        awvalid := false.B
        awaddr  := 0.U
        awid    := 0.U
        awlen   := 0.U
        awsize  := 0.U
        awburst := 0.U
        wvalid  := false.B
        wdata   := 0.U
        wstrb   := 0.U
        wlast   := false.B
        bready  := false.B 
        arvalid := false.B
        araddr  := 0.U
        arid    := 0.U
        arlen   := 0.U
        arsize  := 0.U
        arburst := 0.U
        rready  := false.B
    }
    def initSlave(): Unit = {
        awready := false.B
        
        wready  := false.B   
        bvalid  := false.B   
        bresp   := 0.U       
        bid     := 0.U       
        
        arready := false.B   
        
        rvalid  := false.B   
        rresp   := 0.U       
        rdata   := 0.U       
        rid     := 0.U       
        rlast   := false.B   
    }

    def connectARFrom(master: AXI4Bundle, en: Bool): Unit = {
        this.arvalid := master.arvalid && en
        master.arready := this.arready && en
        this.araddr  := Mux(en, master.araddr, 0.U)
        this.arid    := Mux(en, master.arid, 0.U)
        this.arlen   := Mux(en, master.arlen, 0.U)
        this.arsize  := Mux(en, master.arsize, 0.U)
        this.arburst := Mux(en, master.arburst, 0.U)
    }

    def connectAWFrom(master: AXI4Bundle, en: Bool): Unit = {
        this.awvalid := master.awvalid && en
        master.awready := en && this.awready
        this.awaddr  := Mux(en, master.awaddr, 0.U)
        this.awid    := Mux(en, master.awid, 0.U)
        this.awlen   := Mux(en, master.awlen, 0.U)
        this.awsize  := Mux(en, master.awsize, 0.U)
        this.awburst := Mux(en, master.awburst, 0.U)
    }

  def connectWFrom(master: AXI4Bundle, en: Bool): Unit = {
    this.wvalid := master.wvalid && en
    master.wready := en && this.wready
    this.wdata  := Mux(en, master.wdata, 0.U)
    this.wstrb  := Mux(en, master.wstrb, 0.U)
    this.wlast  := Mux(en, master.wlast, false.B)
  }

  def connectBFrom(master: AXI4Bundle, en: Bool): Unit = {
    master.bvalid := this.bvalid && en
    this.bready := en && master.bready
    master.bresp := Mux(en, this.bresp, 0.U)
    master.bid   := Mux(en, this.bid, 0.U)
  }

  def connectRFrom(master: AXI4Bundle, en: Bool): Unit = {
    master.rvalid := this.rvalid && en
    this.rready := en && master.rready
    master.rdata := Mux(en, this.rdata, 0.U)
    master.rresp := Mux(en, this.rresp, 0.U)
    master.rid   := Mux(en, this.rid, 0.U)
    master.rlast := Mux(en, this.rlast, false.B)
  }
}


