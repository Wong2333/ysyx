module top(
  input clk,
  input rst,
  output [6:0] seg0,
  output [6:0] seg1
);

  reg [7:0] R[4];
  reg [7:0] OUT;
  reg [7:0] PC;

  wire [7:0] data;
  rom u_rom(PC,data);
  
  hex_to_7seg u1_hex_to_7seg(
    .en  	(1   ),
    .hex 	(OUT[3:0]  ),
    .seg 	(seg0  )
  );
  hex_to_7seg u2_hex_to_7seg(
    .en  	(1   ),
    .hex 	(OUT[7:4]  ),
    .seg 	(seg1  )
  );

  wire [1:0] op = data[7:6];
  wire [1:0] rd = data[5:4];
  wire [1:0] rs1 = data[3:2];
  wire [1:0] rs2 = data[1:0];
  wire [3:0] imm = data[3:0];
  wire [3:0] addr = data[5:2];

  always @(posedge clk) begin
    if(rst == 1)begin
      R[0]<=0;
      R[1]<=0;
      R[2]<=0;
      R[3]<=0;
      OUT<=0;
      PC<=0;
    end
    else begin
      case (op)
        2'b00: begin 
          PC<=PC+1;
          R[rd] <=R[rs1] + R[rs2];
        end
        2'b01: begin
          PC<=PC+1;
          OUT<=R[rs2];
        end
        2'b10: begin 
          PC<=PC+1;
          R[rd]<={4'b0000,imm};
        end
        2'b11: begin
          if(R[0] != R[rs2])
            PC<={4'b0000,addr};
          else PC<=PC+1;
        end
      endcase

    end
  end
endmodule
