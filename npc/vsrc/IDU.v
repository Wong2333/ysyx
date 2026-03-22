module IDU(
    input [31:0]pc,
    input [31:0] inst,

    output [4:0]rs1,//寄存器读取
    input [31:0]reg_rdata1,
    output [4:0]rs2,
    input [31:0]reg_rdata2,

    output reg [31:0]imm,//ALU控制信号
    output reg [31:0]src1,
    output reg [31:0]src2,
    output reg [3:0]ALU_ctl,

    output reg [2:0]WB_sel,//1：ALU->reg 2:mem->reg 3:pc+4->reg
    output reg [4:0]reg_waddr,
    output reg reg_we,

    output reg [1:0]pc_sel,//pc寄存器控制 1: ALU->pc 
    output reg pc_we,

    output reg [3:0]mem_ctl,
    output reg mem_wen,
    output reg mem_valid
);
    import "DPI-C" function void ebreak();
    import "DPI-C" function void invalid_instruction(input int pc,input int inst);
    wire [6:0]opcode = inst[6:0];
    wire [4:0]rd = inst[11:7];
    assign rs1 = inst[19:15];
    assign rs2 = inst[24:20];
    wire [2:0]funct3 = inst[14:12];
    wire [6:0]funct7 = inst[31:25];
    wire [31:0]immI = { {20{inst[31]}}, inst[31:20]};
    wire [31:0]immU = {inst[31:12],12'b0};
    wire [31:0]immS = {{20{inst[31]}},inst[31:25],inst[11:7]};

    always @(*) begin
        imm     = 32'b0;
        src1    = 32'b0;
        src2    = 32'b0;
        ALU_ctl = 4'b0;

        reg_waddr = 5'b0;
        reg_we = 1'b0;
        WB_sel = 3'b00;

        pc_sel = 2'b00;
        pc_we=1'b0;

        mem_ctl=4'b0;
        mem_wen=1'b0;
        mem_valid=1'b0;
        case (opcode)
        7'b0010011:begin
            if(funct3 == 3'b000)begin // addi
                imm=immI;
                src1=reg_rdata1;
                ALU_ctl=4'b0001;
                reg_waddr=rd;
                reg_we = 1'b1;
                WB_sel = 3'b01;
            end
        end
        7'b1100111:begin
            if(funct3 == 3'b000)begin // jalr
                imm=immI;
                src1=reg_rdata1;
                ALU_ctl=4'b0001;
                reg_waddr=rd;
                reg_we = 1'b1;
                pc_we = 1'b1;
                WB_sel = 3'b11;
                pc_sel = 2'b01;
            end
        end
        7'b1110011:begin
            if(funct3 == 3'b000)begin //ebreak
                if(immI == 32'b1 
                && rs1 == 5'b00000 
                && rd == 5'b00000
                )begin
                    ebreak();
                end
            end
        end
        7'b0110011:begin // add
            if(funct3 == 3'b000 && funct7 == 7'b0000000)begin
                src1=reg_rdata1;
                src2=reg_rdata2;
                ALU_ctl=4'b0010;
                reg_waddr=rd;
                reg_we = 1'b1;
                WB_sel = 3'b01;
            end
        end
        7'b0110111:begin //lui
            imm=immU;
            reg_waddr=rd;
            reg_we = 1'b1;
            WB_sel = 3'b100;
            
        end
        7'b0000011:begin //lw
            if(funct3 == 3'b010)begin
                imm=immI;
                src1=reg_rdata1;
                ALU_ctl=4'b0001;
                reg_waddr=rd;
                reg_we = 1'b1;
                WB_sel = 3'b10;
                mem_ctl = 4'b1;
                mem_valid = 1;
            end
            else if(funct3==3'b100)begin //lbu
                imm=immI;
                src1=reg_rdata1;
                reg_waddr=rd;
                reg_we = 1'b1;
                ALU_ctl=4'b0001;
                WB_sel = 3'b10;
                mem_ctl = 4'b11;
                mem_valid = 1;
            end
        end
        7'b0100011:begin
            if(funct3 == 3'b010)begin//sw
                imm=immS;
                src1=reg_rdata1;
                src2=reg_rdata2;
                ALU_ctl=4'b0001;
                mem_ctl=4;
                mem_valid = 1;
                mem_wen=1;
            end
            else if(funct3 ==  3'b000)begin//sb
                imm=immS;
                src1=reg_rdata1;
                src2=reg_rdata2;
                ALU_ctl=4'b0001;
                mem_ctl=5;
                mem_valid = 1;
                mem_wen=1;
            end
        end
        default:begin
            invalid_instruction(pc,inst);
        end
            
        endcase
    end

endmodule
