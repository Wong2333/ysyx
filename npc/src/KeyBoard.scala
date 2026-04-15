package npc
import chisel3._
import chisel3.util._
//clrn,ps2_clk,ps2_data,data,ready,nextdata_n,overflow
class KeyBoard extends Module{
    val io = IO(new Bundle{
        val clrn = Input(Bool())
        val ps2_clk = Input(Bool())
        val nextdata_n = Input(Bool())
        val ps2_data = Input(UInt(1.W))
        val data = Output(UInt(8.W))
        val ready = Output(Bool())
        val overflow = Output(Bool())
    })
    val buffer = Reg(UInt(10.W))
    //reg [7:0] fifo[7:0];
    val fifo = Reg(Vec(8, UInt(8.W)))
    //reg [2:0] w_ptr,r_ptr;
    val w_ptr = Reg(UInt(3.W))
    val r_ptr = Reg(UInt(3.W))
    val count = Reg(UInt(4.W))
    val ps2_clk_sync = Reg(UInt(3.W))

    val sampling = ps2_clk_sync(2) & ~ps2_clk_sync(1)

    when(io.clrn === 0.U){
        w_ptr := 0.U
        r_ptr := 0.U
        count := 0.U
        ps2_clk_sync := 0.U
        io.overflow := 0.U
        io.ready := 0.U
    }.otherwise{
        when(io.ready){
            when(io.nextdata_n === 0.U){
                r_ptr := r_ptr + 1.U
                when(w_ptr === r_ptr + 1.U){
                    io.ready := 0.U
                }
            }
        }
        when(sampling){
            when(count === 10.U){
                when(buffer(0) === 0.U && 
                    io.ps2_clk === 1.U &&
                    buffer(9,1).xorR
                ){
                    fifo(w_ptr) := buffer(8,1)
                    w_ptr := w_ptr + 1.U
                    io.ready := true.B
                    io.overflow := io.overflow | (r_ptr === (w_ptr + 1.U))
                }
                count := 0.U
            }.otherwise{
                buffer(count) := io.ps2_data
                count := count + 1.U
            }
        }
    }

    ps2_clk_sync :=  Cat(ps2_clk_sync(1,0),io.ps2_clk)
}