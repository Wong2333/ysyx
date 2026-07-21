 
module DifftestReset(
    input reset
);
    import "DPI-C" context function void difftest_reset();
    always @(*) begin
        if(reset)begin
            difftest_reset();
        end
    end
endmodule

