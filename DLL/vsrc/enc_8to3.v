module enc_8to3(
  input[7:0] a,
  input enable,
  output reg[2:0] s,
  output reg zero
);
  integer i = 0;
  always @(*) begin
    if (enable) begin
      case(a)
        8'b00000001 : s = 3'b000;
        8'b00000010 : s = 3'b001;
        8'b00000100 : s = 3'b010;
        8'b00001000 : s = 3'b011;
        8'b00010000 : s = 3'b100;
        8'b00100000 : s = 3'b101;
        8'b01000000 : s = 3'b110;
        8'b10000000 : s = 3'b111;
        default: s = 3'bxxx;
      endcase
    end
    else begin
      s = 3'bxxx;
      zero = 1'bx;
    end
  end

  
endmodule

