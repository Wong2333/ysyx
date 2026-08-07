module RegBlackBox(
    input         clock,
    input  [31:0] wdata,
    input  [4:0]  waddr,
    input         wen,
    input  [4:0]  raddr1,
    output [31:0] rdata1,
    input  [4:0]  raddr2,
    output [31:0] rdata2
);
    reg [31:0] rf [31:0];

    // 两级读 mux：低 3 位做 8:1（4 组），高 2 位做 4:1 —— 显式两级结构供综合器优化
    wire [31:0] r1_g [0:3];
    wire [31:0] r2_g [0:3];
    genvar gr;
    generate
      for (gr = 0; gr < 4; gr = gr + 1) begin : rd_mux
        assign r1_g[gr] = 32'h0
          | ({32{raddr1[2:0] == 3'd0}} & rf[gr*8+0])
          | ({32{raddr1[2:0] == 3'd1}} & rf[gr*8+1])
          | ({32{raddr1[2:0] == 3'd2}} & rf[gr*8+2])
          | ({32{raddr1[2:0] == 3'd3}} & rf[gr*8+3])
          | ({32{raddr1[2:0] == 3'd4}} & rf[gr*8+4])
          | ({32{raddr1[2:0] == 3'd5}} & rf[gr*8+5])
          | ({32{raddr1[2:0] == 3'd6}} & rf[gr*8+6])
          | ({32{raddr1[2:0] == 3'd7}} & rf[gr*8+7]);
        assign r2_g[gr] = 32'h0
          | ({32{raddr2[2:0] == 3'd0}} & rf[gr*8+0])
          | ({32{raddr2[2:0] == 3'd1}} & rf[gr*8+1])
          | ({32{raddr2[2:0] == 3'd2}} & rf[gr*8+2])
          | ({32{raddr2[2:0] == 3'd3}} & rf[gr*8+3])
          | ({32{raddr2[2:0] == 3'd4}} & rf[gr*8+4])
          | ({32{raddr2[2:0] == 3'd5}} & rf[gr*8+5])
          | ({32{raddr2[2:0] == 3'd6}} & rf[gr*8+6])
          | ({32{raddr2[2:0] == 3'd7}} & rf[gr*8+7]);
      end
    endgenerate

    assign rdata1 = (raddr1 == 5'b0) ? 32'b0
      : raddr1[4:3] == 2'd0 ? r1_g[0] : raddr1[4:3] == 2'd1 ? r1_g[1]
      : raddr1[4:3] == 2'd2 ? r1_g[2] : r1_g[3];
    assign rdata2 = (raddr2 == 5'b0) ? 32'b0
      : raddr2[4:3] == 2'd0 ? r2_g[0] : raddr2[4:3] == 2'd1 ? r2_g[1]
      : raddr2[4:3] == 2'd2 ? r2_g[2] : r2_g[3];

    // 时钟使能实现：D 端直连 wdata，写选择放在 CE 引脚（比 D 端 mux 路径短）
    genvar gi;
    generate
      for (gi = 0; gi < 32; gi = gi + 1) begin : rf_ce
        always @(posedge clock) begin
          if (wen && waddr == gi[4:0]) rf[gi] <= wdata;
        end
      end
    endgenerate

`ifndef SYNTHESIS
    // DPI-C 必须和 rf 在同一个 module 作用域下！
    export "DPI-C" task read_reg;
    task read_reg(input int addr, output int data);
        data = rf[addr[4:0]];
    endtask
`endif
endmodule
