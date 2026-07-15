 
module SkipDifftest(
    input skip
);
    import "DPI-C" context function void difftest_skip_ref();
    always @(*) begin
        if(skip)begin
            difftest_skip_ref();
        end
    end
endmodule

