module mtrace (
    input is_write,
    input [31:0] addr,
    input [31:0] data,
    input [3:0] size,
    input valid
);
    import "DPI-C" context function void mtrace(input byte is_write,input int addr, input int data, input int size);
    always@(*) begin
        if(valid) mtrace({7'b0, is_write}, addr, data, {28'd0, size});
    end
endmodule
