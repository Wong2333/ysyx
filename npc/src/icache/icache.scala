// package npc.icache
// import chisel3._
// import chisel3.util._
// import npc.AXI._

// class icache(block_size :Int, block_num :Int) extends Module{
//     val cache = SyncReadMem(block_size*block_num, UInt(32.W))
//     val offset_bits = log2Ceil(block_size)
//     val index_bits = log2Ceil(block_num)
//     val tag_bits = 32 - offset_bits - index_bits
//     val tag_array = SyncReadMem(block_size, UInt(tag_bits.W))
//     val state_array = RegInit(Vec(block_num,false.B))

//     val req = IO(Decoupled(new Bundle {
//         val addr = Input(UInt(32.W))
//     }))
//     val resp = IO(Decoupled(new Bundle {
//         val rdata = Output(UInt(32.W))
//     }))
//     val AXI4_port = IO(new AXI4Bundle)

//     val addr_reg = Reg(UInt(32.W))
//     val data_reg = Reg(UInt(32.W))

//     val req_addr = Mux(req.fire, req.bits.addr, addr_reg)
//     val offset = req_addr(offset_bits-1,0)
//     val index = req_addr(offset_bits + index_bits-1, offset_bits)
//     val tag = req_addr(31, offset_bits + index_bits)

//     val s_idle :: s_check :: s_mem_req :: s_mem_resp :: s_ready :: Nil = Enum(5)
//     val state = RegInit(s_idle)

//     val self_hit = tag_array.read(index) === tag && state_array(index)
//     val sram_addr = Cat(index, offset)(index_bits + offset_bits - 1, 2)
//     resp.bits.rdata := cache.read(sram_addr)

//     switch(state){
//         is(s_idle){
//             req.ready := true.B
//             when(req.valid){
//                 addr_reg := req.bits.addr
//                 state := s_check
//             }
//         }
//         is(s_check){
//             when(self_hit){
//                 resp.valid := true.B
//                 state := Mux(resp.fire, s_idle, s_ready)
//             }.otherwise{
//                 state := 
//             }
//         }
//         is(s_ready){
//             resp.valid := true.B
//             when(resp.fire){
//                 state := s_idle
//             }
//         }

//     }
// }