package npc

import chisel3._

class Top extends Module{

    val io = IO(new Bundle{
        val clrn = Input(Bool())
        val ps2_clk = Input(Bool())
        val ps2_data = Input(UInt(1.W))
        val seg0 = Output(UInt(7.W))
        val seg1 = Output(UInt(7.W))
    })
    val nextdata_n = Reg(Bool())
    val up = Reg(Bool())
    val data = Reg(UInt(8.W))
    val press = Reg(Bool())

    val keyboard = Module(new KeyBoard())
    keyboard.io.clrn := io.clrn
    keyboard.io.ps2_clk := io.ps2_clk
    keyboard.io.ps2_data := io.ps2_data
    keyboard.io.nextdata_n := nextdata_n
    when(io.clrn === false.B){
        nextdata_n := true.B
        up := false.B
        press := false.B
    }.otherwise{
        when(nextdata_n === false.B){
            nextdata_n := true.B
        }
        when(keyboard.io.ready === true.B){
            when(keyboard.io.data === "hF0".U){
                up := true.B
                press := false.B;
            }.otherwise{
                when(!up){
                    press := true.B
                }
                up := false.B
            }
            nextdata_n := false.B
        }
    }



    val seg0 = Module(new HexToSevenSeg())
    val seg1 = Module(new HexToSevenSeg())
    seg0.io.hex_in := keyboard.io.data(3,0)
    seg1.io.hex_in := keyboard.io.data(7,0)
    when(press){
        printf(p"data: ${Hexadecimal(keyboard.io.data)}")
        io.seg0 := seg0.io.seg_out
        io.seg1 := seg1.io.seg_out
    }.otherwise{
        io.seg0 := "b1111111".U
        io.seg1 := "b1111111".U        
    }

}