module PmemReadBlackBox (
    input clock,
    input [31:0] raddr,
    output reg[31:0] rdata
);
    import "DPI-C" context function int pmem_read(input int raddr);
    
    always @(posedge clock) begin
        rdata <= pmem_read(raddr);
    end

endmodule
