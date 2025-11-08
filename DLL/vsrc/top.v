module top(
  input[7:0] n,
  output reg[7:0] seg_output
);
  wire[3:0] num;
  wire zero;
  
  enc_8to3 u_enc_8to3(
    .a      	(n       ),
    .enable 	(1  ),
    .s      	(num[2:0]     ),
    .zero   	(zero    )
  );
  assign num[3] = 0;
  seg u_seg(
    .num(num),
    .y(seg_output)
  );

  
  
endmodule


