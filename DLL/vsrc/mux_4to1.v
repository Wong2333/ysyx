module mux4to1(
  input[3:0] a,
  input[1:0] s,
  output y
);
  assign y = (s==2'b00)?a[0]:
             (s==2'b01)?a[1]:
             (s==2'b10)?a[2]:
             (s==2'b11)?a[3]:1'bx;

  
endmodule