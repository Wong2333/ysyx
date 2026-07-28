module InvalidInstructionBlackBox (
    input is_write,
    input [31:0] addr,
    input [31:0] data,
    input [3:0] size,
    input valid
);
    import "DPI-C" context function void mtrace(input char is_write,input int addr, input int data, input int size);
    always@(*) begin
        if(invalid) mtrace(is_write, addr, data, size);
    end
endmodule
