module InvalidInstructionBlackBox (
    input [31:0] pc,
    input [31:0] inst,
    input invalid
);
    import "DPI-C" context function void invalid_instruction(input int pc,input int inst);
    always@(*) begin
        if(invalid) invalid_instruction(pc,inst);
    end
endmodule
