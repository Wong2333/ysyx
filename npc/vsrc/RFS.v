module RFS #(ADDR_WIDTH = 5, DATA_WIDTH = 32) (
    input clk,
    input [DATA_WIDTH-1:0] wdata,
    input [ADDR_WIDTH-1:0] waddr,
    input wen,
    input [ADDR_WIDTH-1:0]raddr1,
    output [DATA_WIDTH-1:0]rdata1,
    input [ADDR_WIDTH-1:0]raddr2,
    output [DATA_WIDTH-1:0]rdata2
);
    integer i;
    reg [DATA_WIDTH-1:0] rf [2**ADDR_WIDTH-1:0];
    assign rdata1 = raddr1==5'b0 ? 32'b0 : rf[raddr1];
    assign rdata2 = raddr2==5'b0 ? 32'b0 : rf[raddr2];

    export "DPI-C" task read_reg;
    task read_reg(input int addr, output int data);
        data = rf[addr[4:0]];
    endtask

    always @(posedge clk) begin
        if (wen) begin
            rf[waddr] <= wdata;
            // $display("reg[%h] <= %h",waddr,wdata); 
        end

    end

endmodule
