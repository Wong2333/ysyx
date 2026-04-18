module RFS_BlackBox(
    input         clk,
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

    always @(posedge clk) begin
        if (wen) rf[waddr] <= wdata;
    end

    // DPI-C 必须和 rf 在同一个 module 作用域下！
    export "DPI-C" task read_reg;
    task read_reg(input int addr, output int data);
        data = rf[addr[4:0]];
    endtask
endmodule
