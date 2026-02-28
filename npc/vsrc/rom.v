module rom(input [7:0]addr,output reg[7:0] data);

always @(addr) begin
    case (addr)
        8'h0: data=8'b10001010;
        8'h1: data=8'b10010000;
        8'h2: data=8'b10100000;
        8'h3: data=8'b10110001;
        8'h4: data=8'b00010111;
        8'h5: data=8'b00101001;
        8'h6: data=8'b11010001;
        8'h7: data=8'b01000010;
        8'h8: data=8'b11100011;
        default: data=8'hFF;
    endcase
end
endmodule
