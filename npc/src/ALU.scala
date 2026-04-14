package npc

import chisel3._
import chisel3.util._

class ALU(n:Int) extends Module{
    val io = IO(new Bundle{
        val in1 = Input(UInt(n.W))
        val in2 = Input(UInt(n.W))
        val aluOp = Input(UInt(3.W))

        val out = Output(UInt(n.W))
        val zero = Output(Bool())
        val overflow = Output(Bool())
        val carry = Output(Bool())
    })

    val result   = WireDefault(0.U(n.W))
    val zero     = WireDefault(false.B)
    val overflow = WireDefault(false.B)
    val carry    = WireDefault(false.B)
    val addout = io.in1 +& io.in2

    val subout = io.in1 +& (~io.in2) + 1.U

    switch(io.aluOp){
        is("b000".U){
            result := addout(n-1,0)
            zero := (addout(n-1,0) === 0.U)
            carry := addout(n)
            overflow := (io.in1(n-1) === io.in2(n-1)) && 
                (io.in1(n-1) =/= addout(n-1))
        }
        is("b001".U){
            result := subout(n-1,0)
            zero := (subout(n-1,0) === 0.U)
            carry := !subout(n)
            overflow := (io.in1(n-1) =/= io.in2(n-1)) && 
                (io.in1(n-1) =/= subout(n-1))              
        }
        is("b010".U){
            result := ~io.in1
            zero := (!io.in1 === 0.U)
        }
        is("b011".U){
            result := io.in1 & io.in2
            zero := (io.in1 & io.in2) === 0.U
        }
        is("b100".U){
            result := io.in1 | io.in2
            zero := (io.in1 | io.in2) === 0.U   
        }
        is("b101".U){
            result := io.in1 ^ io.in2
            zero := (io.in1 ^ io.in2) === 0.U   
        }
        is("b110".U){
            val isLess = Mux(io.in1(n-1)=/=io.in2(n-1),
                io.in1(n-1),io.in1 < io.in2)
            result := isLess
            zero := !isLess
        }
        is("b111".U){
            val isEqual = (io.in1 === io.in2)
            result := isEqual
            zero := !isEqual
        }
    }
    io.out := result
    io.zero := zero
    io.overflow := overflow
    io.carry := carry
}