module ALU(
    input[2:0] select_mode,
    input[3:0] A,
    input[3:0] B,
    input clk,
    output reg[3:0] Y,
    output reg ZF,
    output reg OF,
    output reg CF);

    wire [3:0] Y_add;
    wire ZF_add,CF_add,OF_add;
    add_module u_add_module(A,B,Y_add,ZF_add,OF_add,CF_add);

    wire [3:0] Y_sub;
    wire ZF_sub,CF_sub,OF_sub;
    sub_module u_sub_module(A,B,Y_sub,ZF_sub,OF_sub,CF_sub);

    wire [3:0] Y_not;
    not_module u_not_module(A,Y_not);

    wire [3:0] Y_and;
    and_module u_and_module(A,B,Y_and);

    wire [3:0] Y_or;
    or_module u_or_module(A,B,Y_or);

    wire [3:0] Y_xor;
    xor_module u_xor_module(A,B,Y_xor);

    wire Y_less;
    less_module u_less_module(A,B,Y_less);

    wire Y_equ;
    equal_module u_equal_module(A,B,Y_equ);

    always @(posedge clk) begin
        case(select_mode)
            3'b000: begin 
                Y<=Y_add;
                ZF<=ZF_add;
                CF<=CF_add;
                OF<=OF_add;
            end

            3'b001: begin 
                Y<=Y_sub;
                ZF<=ZF_sub;
                CF<=CF_sub;
                OF<=OF_sub;
            end

            3'b010: begin 
                Y<=Y_not;
                ZF<=1'b0;
                CF<=1'b0;
                OF<=1'b0;
            end

            3'b011: begin 
                Y<=Y_and;
                ZF<=1'b0;
                CF<=1'b0;
                OF<=1'b0;
            end

            3'b100: begin 
                Y<=Y_or;
                ZF<=1'b0;
                CF<=1'b0;
                OF<=1'b0;
            end

            3'b101: begin 
                Y<=Y_xor;
                ZF<=1'b0;
                CF<=1'b0;
                OF<=1'b0;
            end

            3'b110: begin 
                Y<={3'b000,Y_less};
                ZF<=1'b0;
                CF<=1'b0;
                OF<=1'b0;
            end

            3'b111: begin 
                Y<={3'b000,Y_equ};
                ZF<=1'b0;
                CF<=1'b0;
                OF<=1'b0;
            end

            default: begin Y <= 4'bxxx;ZF<=1'bx;OF<=1'bx;CF<=1'bx; end
        endcase
    end

endmodule

module add_module(    
    input[3:0] A,
    input[3:0] B,
    output [3:0] Y,
    output ZF,
    output OF,
    output CF);

    wire y0;

    assign {y0,Y} = {1'b0,A} + {1'b0,B};
    assign ZF = (Y == 4'b0000)? 1 : 0;

    assign OF = (A[3] != B[3])?0:
                (A[3] == Y[3])?0:1;
    assign CF = (y0==1'b1)?1:0;

endmodule

module sub_module(
    input[3:0] A,
    input[3:0] B,
    output[3:0] Y,
    output ZF,
    output OF,
    output CF);
    assign Y = A - B;
    assign ZF = (Y == 4'b0000);
    assign CF = (A < B);  // 借位：A小于B时需要借位
    assign OF = (A[3] != B[3]) && (A[3] != Y[3]);  // 溢出判断
endmodule

module not_module(    
    input[3:0] A,
    output[3:0] Y);
    assign Y = ~A;
endmodule

module and_module(    
    input[3:0] A,
    input[3:0] B,
    output[3:0] Y);
    assign Y = A & B;

endmodule

module or_module(    
    input[3:0] A,
    input[3:0] B,
    output[3:0] Y);
    assign Y = A | B;
    
endmodule

module xor_module(    
    input[3:0] A,
    input[3:0] B,
    output[3:0] Y);
    assign Y = A ^ B;
    
endmodule

module less_module(    
    input[3:0] A,
    input[3:0] B,
    output Y);
    assign Y = $signed(A) < $signed(B);
    
endmodule

module equal_module(    
    input[3:0] A,
    input[3:0] B,
    output Y);
    assign Y = (A == B);
    
endmodule