module PmemReadBlackBox (
    input [31:0] raddr,
    input valid,
    output reg[31:0] rdata
);
    import "DPI-C" context function int pmem_read(input int raddr,input int valid);

    always @(*) begin
        rdata = pmem_read(raddr,{31'b0,valid});
    end
endmodule
