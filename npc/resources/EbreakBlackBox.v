module EbreakBlackBox (
    input ebreak_trap
);
`ifndef SYNTHESIS
    import "DPI-C" function void ebreak();
    always @(*) begin
        if(ebreak_trap)begin
            ebreak();
        end
    end
`endif
endmodule
