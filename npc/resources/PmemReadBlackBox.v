module PmemReadBlackBox (
    input [31:0] raddr,
    input valid,
    output reg[31:0] rdata,
    output error
);
    import "DPI-C" context function int pmem_read(input int raddr,input int valid, output int error);
    reg [31:0] _error;
    assign error = _error[0];
    always @(*) begin
        rdata = pmem_read(raddr,{31'b0,valid},_error);
    end
endmodule
