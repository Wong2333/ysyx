 
module PmemWriteBlackBox(
    input [31:0] waddr,
    input [31:0] wdata,
    input [3:0] wmask,
    input valid,
    output reg error
);
    import "DPI-C" context function int pmem_write(input int waddr, input int wdata, input byte wmask, input int valid);
    always @(*) begin
        error = pmem_write(waddr, wdata, {4'b0,wmask}, {31'b0,valid});
    end
endmodule
