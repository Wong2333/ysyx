parameter seg_0 = 7'b0_000_001;
parameter seg_1 = 7'b1_001_111;
parameter seg_2 = 7'b0_010_010;
parameter seg_3 = 7'b0_000_110;
parameter seg_4 = 7'b1_001_100;
parameter seg_5 = 7'b0_100_100;
parameter seg_6 = 7'b0_100_000;
parameter seg_7 = 7'b0_001_111;
parameter seg_8 = 7'b0_000_000;
parameter seg_9 = 7'b0_000_100;
parameter seg_X = 8'b1_111_111_1;
parameter seg_sign = 8'b1_111_110_1;

parameter num_0 = 0;
parameter num_1 = 1;
parameter num_2 = 2;
parameter num_3 = 3;
parameter num_4 = 4;
parameter num_5 = 5;
parameter num_6 = 6;
parameter num_7 = 7;
parameter num_8 = 8;
parameter num_9 = 9;
parameter num_sign = 10;

parameter num_X = 15;




module num_to_seg(
  input[3:0] num,
  input dot,
  output[7:0] seg
);
// transform a number that is less than 10 to seg's format 
  
  assign seg = (num==num_0)?{seg_0,dot}:
      (num==num_1)?{seg_1,dot}:
      (num==num_2)?{seg_2,dot}:
      (num==num_3)?{seg_3,dot}:
      (num==num_4)?{seg_4,dot}:
      (num==num_5)?{seg_5,dot}:
      (num==num_6)?{seg_6,dot}:
      (num==num_7)?{seg_7,dot}:
      (num==num_8)?{seg_8,dot}:
      (num==num_9)?{seg_9,dot}:
      (num==num_sign)?seg_sign:seg_X;
  
endmodule

module get_one_num(
  input[31:0] num_in,
  output[31:0] num_out,
  output[3:0] num
);
  wire [31:0] _1;
  assign _1 = (num_in % 10);
  assign num_out = (num_in / 10);
  assign num = _1[3:0];

endmodule


module _number32_to_seg_(
  input[31:0] num_in,
  input sign,
  input clk,
  output reg[7:0] seg0,
  output reg[7:0] seg1,
  output reg[7:0] seg2,
  output reg[7:0] seg3,
  output reg[7:0] seg4,
  output reg[7:0] seg5,
  output reg[7:0] seg6,
  output reg[7:0] seg7
);

  reg[31:0] num_reg; // store the input number
  reg[3:0] count; // cycle 0-8

  initial begin
    count = 9;
  end
  reg isSign;
  initial begin
    isSign=0;
  end
  wire [31:0] num_out;// the num is left after get one num
  wire [3:0] one_num; // the one_num get from 32_num
  wire[7:0] seg; // transformed from one_num, which can be input seg_reg directly
  // if num_reg is 0, the one_num would not be show on seg
  wire[3:0] one_num_no_front_zero = num_reg!=0?one_num:
    sign!=1?num_X:
    isSign==1?num_X:num_sign;

  get_one_num u_get_one_num(num_reg,num_out,one_num);
  num_to_seg u_num_to_seg(one_num_no_front_zero,1,seg);
  
  always @(posedge clk) begin
    if (num_in!=0) begin

    end
  end
  //9 cycles: 0-cycle:fetch num  1...8-cycle:render seg
  always @(posedge clk) begin
    if (num_in==0) begin
      seg0<={seg_0,1'b1};
      seg1<={seg_X};
      seg2<={seg_X};
      seg2<={seg_X};
      seg3<={seg_X};
      seg4<={seg_X};
      seg5<={seg_X};
      seg6<={seg_X};
      seg7<={seg_X};
    end
    else begin
      $display(isSign);
      if(count==9) begin count=0; isSign<=0; end
      else count=count+1;
      if(seg=={seg_sign}) isSign<=1;
      case(count)
        0:begin num_reg <= num_in; end
        1:begin seg0 <= seg;num_reg<=num_out; end // update seg and num_reg
        2:begin seg1 <= seg;num_reg<=num_out; end
        3:begin seg2 <= seg;num_reg<=num_out; end
        4:begin seg3 <= seg;num_reg<=num_out; end
        5:begin seg4 <= seg;num_reg<=num_out; end
        6:begin seg5 <= seg;num_reg<=num_out; end
        7:begin seg6 <= seg;num_reg<=num_out; end
        8:begin seg7 <= seg;num_reg<=num_out; end
      endcase
    end
  end
endmodule
