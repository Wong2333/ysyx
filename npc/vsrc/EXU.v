module EXU(
    input [31:0]imm,
    input [31:0]src1,
    input [31:0]src2,
    input [3:0]ALU_ctl,
    output reg [31:0]out
);
    always @(*) begin
        case (ALU_ctl)
            4'b0001: begin // src1 + imm
                out=src1+imm;
            end
            4'b0010: begin
                out=src1+src2;
            end
            default:begin
                out=32'b0;
            end
        endcase
    end
endmodule
