 
module PmemWriteBlackBox(
    input clock,
    input [31:0] waddr,
    input [31:0] wdata,
    input [3:0] wmask,
    input we
);
    import "DPI-C" context function void pmem_write(input int waddr, input int wdata, input byte wmask);
    always @(posedge clock) begin
        if(we) pmem_write(waddr, wdata, {4'b0,wmask});
    end
endmodule
