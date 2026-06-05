module PmemReadBlackBox (
    input [31:0] raddr,
    output [31:0] rdata
);
    import "DPI-C" context function int pmem_read(input int raddr);
    assign rdata = pmem_read(raddr);
endmodule
