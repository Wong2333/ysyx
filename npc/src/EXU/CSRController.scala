package npc.EXUs

import chisel3._
import chisel3.util._
import npc.DecodeBundle
import npc.WriteBackPortBundle
import npc.EbreakBlackBox

object CSRControl extends ChiselEnum{
    val none,
    ebreak,
    ecall,
    mret,
    csrrw,
    csrrs = Value
}

class ysyx_26030092_CSRController extends Module{
    val in = IO(Input(new Bundle {
        val ctl = CSRControl()
        val decode = new DecodeBundle()

        val AluResult = UInt(32.W)

        // 译码阶段（IDU）预读的寄存器数据
        val rs1_data = UInt(32.W)
        val csr_rdata = UInt(32.W)

    }))

    val out = IO(Output(new WriteBackPortBundle))

    val ebreak = Module(new EbreakBlackBox())
    ebreak.io.ebreak_trap := false.B

    out :=  0.U.asTypeOf(new WriteBackPortBundle)
    switch(in.ctl){
        is(CSRControl.ebreak){
            ebreak.io.ebreak_trap := true.B
        }
        is(CSRControl.ecall){
            out.Exception_Code := 11.U
            out.PC := in.decode.pc
            out.isTrap := true.B
        }
        is(CSRControl.mret){
            out.PCNext := in.csr_rdata
            out.PCWrite := true.B
        }
        is(CSRControl.csrrs){
            out.RegWaddr := in.decode.rd
            out.RegWdata := in.csr_rdata
            out.RegWrite := true.B
            out.CSRAddr := in.decode.imm(11,0)
            out.CSRData := in.AluResult
            out.CSRWrite := true.B
        }
        is(CSRControl.csrrw){
            out.RegWaddr := in.decode.rd
            out.RegWdata := in.csr_rdata
            out.RegWrite := true.B
            out.CSRAddr := in.decode.imm(11,0)
            out.CSRData := in.rs1_data
            out.CSRWrite := true.B
        }
    }
}

