module EbreakBlackBox (
    input ebreak_trap
);
    import "DPI-C" function void ebreak();   
    always @(*) begin
        if(trap)begin 
            ebreak();
        end
    end
endmodule
