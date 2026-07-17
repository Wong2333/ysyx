 module trace(
    input [31:0]pc,
    input [31:0]inst
);
    import "DPI-C" context function void trace(input int pc, input int inst);
    always @(*) begin
        trace(pc,inst);
    end
endmodule
