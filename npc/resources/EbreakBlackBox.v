module EbreakBlackBox (
    input ebreak_trap
);
    import "DPI-C" function void ebreak();   
    always @(*) begin
        if(ebreak_trap)begin 
            ebreak();
        end
    end
endmodule
