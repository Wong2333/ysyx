 
module DifftestCommit(
    input commit
);
    import "DPI-C" context function void difftest_commit();
    always @(*) begin
        if(commit)begin
            difftest_commit();
        end
    end
endmodule

