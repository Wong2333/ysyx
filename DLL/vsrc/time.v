parameter ticks = 100000;

module time_counter(
  input clk_in,
  output clk_out
);
  reg[31:0] count_clk;
  reg clk_reg;
  assign clk_out=clk_reg;

  initial begin
    count_clk=0;
    clk_reg=0;
  end

  always @(posedge clk_in) begin
      if(count_clk > ticks) begin
          count_clk <= 0;
          clk_reg <= ~clk_reg;
      end
      else count_clk <= count_clk + 1;
  end

endmodule