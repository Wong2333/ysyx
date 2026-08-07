module Commit(
    input [31:0] pc,
    input valid,
    input clock,
    input reset,
    input mem_commit,
    input [31:0] mem_addr
);
`ifndef SYNTHESIS
    import "DPI-C" context function void commit(input int pc, input int mem_commit, input int mem_addr);
    always @(posedge clock) begin
        if(valid) begin
            commit(pc, mem_commit, mem_addr);
        end
    end
`endif
endmodule
