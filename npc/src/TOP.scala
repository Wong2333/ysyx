package gcd

import chisel3._

class top extends Module{

    val io = IO(new Bundle{
    val value1        = Input(UInt(16.W))
    val value2        = Input(UInt(16.W))
    val loadingValues = Input(Bool())
    val outputGCD     = Output(UInt(16.W))
    val outputValid   = Output(Bool())
    });
    val gcd_u = new gcd.GCD();
    gcd_u.io <> io;
}