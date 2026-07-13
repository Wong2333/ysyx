package npc.EXUs

import chisel3._
import chisel3.util._
import npc.DecodeBundle
import npc.WriteBackPortBundle
import npc.CSRReadPortBundle
import npc.ReadRegPortBundle
import npc.EbreakBlackBox

object CSRControl extends ChiselEnum{
    val none,
    ebreak,
    ecall,
    mret,
    csrrw,
    csrrs = Value
}

class CSRController extends Module{
    val in = IO(Input(new Bundle {
        val ctl = CSRControl()
        val decode = new DecodeBundle()

        val AluResult = UInt(32.W)

    }))
    val RegReadPort = IO(new ReadRegPortBundle)
    val CSRReadPort = IO(new CSRReadPortBundle)

    val out = IO(Output(new WriteBackPortBundle))

    val ebreak = Module(new EbreakBlackBox())
    ebreak.io.ebreak_trap := false.B
    
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
            CSRReadPort.CSRRAddr := "h341".U
            out.PCNext := CSRReadPort.CSRRData
            out.PCWrite := true.B
        }
        is(CSRControl.csrrs){
            CSRReadPort.CSRRAddr := in.decode.imm(11,0)
            out.RegWaddr := in.decode.rd
            out.RegWdata := CSRReadPort.CSRRData
            out.RegWrite := true.B
            out.CSRAddr := in.decode.imm(11,0)
            out.CSRData := in.AluResult
            out.CSRWrite := true.B
        }
        is(CSRControl.csrrw){
            RegReadPort.raddr1 := in.decode.rs1
            CSRReadPort.CSRRAddr := in.decode.imm(11,0)
            out.RegWaddr := in.decode.rd
            out.RegWdata := CSRReadPort.CSRRData
            out.RegWrite := true.B
            out.CSRAddr := in.decode.imm(11,0)
            out.CSRData := RegReadPort.rdata1
            out.CSRWrite := true.B
        }
    }
}

