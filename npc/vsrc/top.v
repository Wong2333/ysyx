module top(
  input clk,
  input rst,
  output [31:0]pc,
  output [31:0]inst
);
  wire [31:0] reg_wdata;
  wire [4:0] reg_waddr;
  wire reg_we;

  wire [4:0] reg_raddr1;
  wire [31:0] reg_rdata1;
  wire [4:0] reg_raddr2;
  wire [31:0] reg_rdata2;


  RFS u_RFS(
    .clk   (clk),
    .wdata (reg_wdata),
    .waddr (reg_waddr),
    .wen   (reg_we),
    .raddr1 (reg_raddr1),
    .rdata1 (reg_rdata1),
    .raddr2 (reg_raddr2),
    .rdata2 (reg_rdata2)
  );

  wire [31:0]pc_next;
  wire pc_we;

  IFU u_IFU(
    .clk     	(clk      ),
    .rst     	(rst      ),
    .pc_next 	(pc_next  ),
    .pc_we   	(pc_we    ),
    .pc      	(pc       ),
    .inst     (inst)
  );

  wire [31:0]imm;
  wire [31:0]src1;
  wire [31:0]src2;
  wire [3:0]ALU_ctl;
  wire [31:0]ALU_out;
  wire [2:0]WB_sel;
  wire [1:0]pc_sel;

  wire mem_valid;
  wire mem_wen;
  wire [31:0]mem_rdata;


  wire [3:0]mem_ctl;
  LSU u_LSU(
    .valid 	(mem_valid  ),
    .wen   	(mem_wen    ),
    .clk(clk),
    .mem_ctl(mem_ctl),//1:LW 2:LB 3:SW 4:SB
    .src1(src1),
    .src2(src2),
    .ALU_out(ALU_out),
    .mem_out(mem_rdata)
  );
  

  IDU u_IDU(
    .pc (pc),
    .inst (inst),

    .rs1(reg_raddr1),//寄存器读取
    .reg_rdata1(reg_rdata1),
    .rs2(reg_raddr2),
    .reg_rdata2(reg_rdata2),

    .imm(imm),
    .src1(src1),
    .src2(src2),
    .ALU_ctl(ALU_ctl),

    .WB_sel(WB_sel),
    .reg_waddr(reg_waddr),
    .reg_we(reg_we),

    .pc_sel(pc_sel),
    .pc_we(pc_we),
    
    .mem_ctl(mem_ctl),
    .mem_wen(mem_wen),
    .mem_valid(mem_valid)
  );

  EXU u_EXU(
    .imm(imm),
    .src1(src1),
    .src2(src2),
    .ALU_ctl(ALU_ctl),
    .out(ALU_out)
  );


  WBU u_WBU(
    .pc           (pc   ),
    .imm          (imm),
    .ALU_out  	  (ALU_out),
    .mem_rdata    (mem_rdata),
    .WB_sel    	  (WB_sel),
    .pc_sel    	  (pc_sel),
    .reg_wdata 	  (reg_wdata),
    .pc_next 	    (pc_next)
  );
  

endmodule

