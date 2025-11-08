module seg(
  input[3:0] num,
  output reg[7:0] y
);
  parameter _0_ = 7'b0_000_001;
  parameter _1_ = 7'b1_001_111;
  parameter _2_ = 7'b0_010_010;
  parameter _3_ = 7'b0_000_110;
  parameter _4_ = 7'b1_001_100;
  parameter _5_ = 7'b0_100_100;
  parameter _6_ = 7'b0_100_000;
  parameter _7_ = 7'b0_001_111;
  parameter _8_ = 7'b0_000_000;

  reg[6:0] l;
  reg dot = 1;

  always @(num) begin
    case(num)
      4'b0000: l = _0_;
      4'b0001: l = _1_;
      4'b0010: l = _2_;
      4'b0011: l = _3_;
      4'b0100: l = _4_;
      4'b0101: l = _5_;
      4'b0110: l = _6_;
      4'b0111: l = _7_;
      4'b1000: l = _8_;
      default: l = 7'bxxxxxxx;
    endcase
    y = {l,dot};
  end
  
endmodule


