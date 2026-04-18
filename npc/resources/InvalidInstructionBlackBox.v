module InvalidInstructionBlackBox (
    input [31:0] pc,
    input [31:0] inst,
    input invalid
);
    import "DPI-C" context function int invalid_instruction(input int pc,input int inst);
    if(invalid) invalid_instruction(pc,inst);
endmodule
