module PmemReadBlackBox (
    input [31:0] raddr,
    input valid,
    output [31:0] rdata
);
    import "DPI-C" context function int pmem_read(input int raddr);
    always @(*) begin
        if(valid) begin
            rdata = pmem_read(raddr);
        end
    end

endmodule
