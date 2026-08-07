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

    assign rdata1 = (raddr1 == 5'b0) ? 32'b0 : rf[raddr1];
    assign rdata2 = (raddr2 == 5'b0) ? 32'b0 : rf[raddr2];

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
