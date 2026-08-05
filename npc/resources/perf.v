module PerfCounter(
    input valid,
    input [31:0] id,
    input [31:0] cnt 
);
`ifndef SYNTHESIS
    import "DPI-C" context function void perf_inc(int id, int cnt);
    always @(*) begin
        if(valid) perf_inc(id, cnt);
    end
`endif
endmodule