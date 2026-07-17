 module trace(
    input [31:0]pc,
    input [31:0]inst,
    input valid
);
    import "DPI-C" context function void trace(input int pc, input int inst);
    always @(*) begin
        if(valid) begin
            trace(pc,inst);
        end
    end
endmodule
