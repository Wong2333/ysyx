 
module SkipDifftest(
    input skip
);
    import "DPI-C" context function void skip_difftest();
    always @(*) begin
        if(skip)begin
            skip_difftest();
        end
    end
endmodule

