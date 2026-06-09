 
module PmemWriteBlackBox(
    input [31:0] waddr,
    input [31:0] wdata,
    input [3:0] wmask,
    input valid
);
    import "DPI-C" context function void pmem_write(input int waddr, input int wdata, input byte wmask, input int valid);
    always @(*) begin
        pmem_write(waddr, wdata, {4'b0,wmask}, valid);
        
    end
endmodule
