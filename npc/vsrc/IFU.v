module IFU(
    input clk,
    input rst,
    input [31:0]pc_next,
    input pc_we,
    output [31:0]pc,
    output [31:0]inst
);

    import "DPI-C" context function int pmem_read(input int raddr);
    reg [31:0] PC;
    assign pc = PC;
    assign inst=pmem_read(PC);
    initial begin
        PC=0;
    end
    always @(posedge clk) begin
        if(rst)begin
            PC<=0;
        end
        else begin
            if(pc_we) begin
                PC <= pc_next;
            end
            else begin
                PC <= PC+4;
            end
            
        end
    end
endmodule
