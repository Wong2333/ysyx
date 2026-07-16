package npc.AXI

import chisel3._
import chisel3.util._

class AXI4Bundle extends Bundle {
    
    // val AW = FlatIO(new Bundle {
    // ================= 写地址通道 (AW) =================
        val awready = Input(Bool())       // Slave 准备好接收写地址
        val awvalid = Output(Bool())      // Master 写地址有效
        val awaddr  = Output(UInt(32.W))  // 写地址 (32位)
        val awid    = Output(UInt(4.W))   // 写操作ID (4位)
        val awlen   = Output(UInt(8.W))   // 突发长度 (8位)
        val awsize  = Output(UInt(3.W))   // 突发大小 (3位)
        val awburst = Output(UInt(2.W))   // 突发类型 (2位)
    // })


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
}


class AXI4Arbiter(n_in : Int) extends Module{
    val in = IO(Vec(n_in, Flipped(new AXI4Bundle)))   // 接收多个 Master 的请求
    val out = IO(new AXI4Bundle)    // 输出给单个 Slave

    // 1. 默认初始化：防止 Chisel 报未连接警告，同时保证未被选中的 Master/Slave 信号保持空闲
    for(i <- 0 until n_in){
        in(i).initSlave()
    }
    out.initMaster()

    // ================= 读通道仲裁 (AR / R) =================
    val r_idle :: r_busy :: Nil = Enum(2)
    val r_status = RegInit(r_idle)
    val r_sel = RegInit(0.U(log2Ceil(n_in).W)) // 记录当前选中的 Master

    switch(r_status){
        is(r_idle){
            // 将所有 in 的 arvalid 组合起来，例如 in(0)有效且 in(1)有效，就是 0b11
            val ar_reqs = VecInit(in.map(_.arvalid)).asUInt
            when(ar_reqs =/= 0.U){
                // 使用优先级编码器，编号小(0)的优先级最高
                r_sel := PriorityEncoder(ar_reqs)
                r_status := r_busy
            }
        }
        is(r_busy){
            // --- 接通 AR 通道 (Master -> Arbiter -> Slave) ---
            out.arvalid := in(r_sel).arvalid
            out.araddr  := in(r_sel).araddr
            out.arid    := in(r_sel).arid
            out.arlen   := in(r_sel).arlen
            out.arsize  := in(r_sel).arsize
            out.arburst := in(r_sel).arburst
            in(r_sel).arready := out.arready

            // --- 接通 R 通道 (Slave -> Arbiter -> Master) ---
            in(r_sel).rvalid := out.rvalid
            in(r_sel).rresp  := out.rresp
            in(r_sel).rdata  := out.rdata
            in(r_sel).rid    := out.rid
            in(r_sel).rlast  := out.rlast
            out.rready  := in(r_sel).rready

            // 🌟 关键：只有当读数据最后一拍握手完成，才释放总线
            when(out.rvalid && out.rready && out.rlast){
                r_status := r_idle
            }
        }
    }

    // ================= 写通道仲裁 (AW / W / B) =================
    val w_idle :: w_busy :: Nil = Enum(2)
    val w_status = RegInit(w_idle)
    val w_sel = RegInit(0.U(log2Ceil(n_in).W)) // 记录当前选中的 Master

    switch(w_status){
        is(w_idle){
            val aw_reqs = VecInit(in.map(_.awvalid)).asUInt
            when(aw_reqs =/= 0.U){
                w_sel := PriorityEncoder(aw_reqs)
                w_status := w_busy
            }
        }
        is(w_busy){
            // --- 接通 AW 通道 ---
            out.awvalid := in(w_sel).awvalid
            out.awaddr  := in(w_sel).awaddr
            out.awid    := in(w_sel).awid
            out.awlen   := in(w_sel).awlen
            out.awsize  := in(w_sel).awsize
            out.awburst := in(w_sel).awburst
            in(w_sel).awready := out.awready

            // --- 接通 W 通道 ---
            out.wvalid := in(w_sel).wvalid
            out.wdata  := in(w_sel).wdata
            out.wstrb  := in(w_sel).wstrb
            out.wlast  := in(w_sel).wlast
            in(w_sel).wready := out.wready

            // --- 接通 B 通道 ---
            in(w_sel).bvalid := out.bvalid
            in(w_sel).bresp  := out.bresp
            in(w_sel).bid    := out.bid
            out.bready := in(w_sel).bready

            // 🌟 关键：只有当写响应握手完成，才释放总线
            when(out.bvalid && out.bready){
                w_status := w_idle
            }
        }
    }
}
