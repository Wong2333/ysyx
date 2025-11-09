module top(
  input sys_clk,
  output[7:0] seg0,
  output[7:0] seg1,
  output[7:0] seg2,
  output[7:0] seg3,
  output[7:0] seg4,
  output[7:0] seg5,
  output[7:0] seg6,
  output[7:0] seg7
);
    reg[31:0] num_reg;
    wire clk;

    // set self clock frequence
    time_counter u_time_counter(sys_clk,clk);
    // show number on seg
    _number32_to_seg_ u_32num_to_seg_(num_reg,1,
      sys_clk,seg0,seg1,seg2,seg3,seg4,seg5,seg6,seg7);


    initial begin
      num_reg=0;
    end
    
    always @(posedge clk) begin
        if(num_reg == 10) num_reg <= 0;
        else num_reg <= num_reg + 1;
    end

endmodule



