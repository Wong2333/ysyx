 module Commit(
    input [31:0]pc,
    input valid
);
`ifndef SYNTHESIS
    import "DPI-C" context function void commit(input int pc);
    always @(*) begin
        if(valid) begin
            commit(pc);
        end
    end
`endif
endmodule
