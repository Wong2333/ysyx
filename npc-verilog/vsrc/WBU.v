module WBU(
    input [31:0]pc,
    input [31:0]ALU_out,
    input [31:0]imm,
    input [31:0]mem_rdata,
    input [2:0]WB_sel,//1：ALU->reg 2:mem->reg 3:pc+4->reg
    input [1:0]pc_sel,//pc寄存器控制 1: ALU->pc 

    output reg [31:0]reg_wdata,
    output reg [31:0]pc_next
);
    always @(*) begin
        case (WB_sel)
        3'b00: reg_wdata=32'b0;
        3'b01: reg_wdata=ALU_out;
        3'b10: begin reg_wdata=mem_rdata;end
        3'b11: reg_wdata=pc+4;
        3'b100: begin reg_wdata=imm;end
        default:reg_wdata=32'b0;
        endcase
    end

    always @(*) begin
        case (pc_sel)
        2'b00: pc_next=32'b0;
        2'b01: pc_next=ALU_out;
        2'b10: pc_next=32'b0;
        2'b11: pc_next=32'b0;
        endcase
    end

endmodule
