module PCBlackBox(
    input clock,
    input reset,
    input [31:0] pc_next,
    input pc_we,
    output [31:0]pc
);
    `include "config.svh"
    reg [31:0] PC;
    assign pc = PC;
    always@(posedge clock) begin
        if(reset)begin
            PC <= CONFIG_START_ADDR;
        end
        else begin
            if(pc_we)begin
                PC <= pc_next;
            end
            else begin
                PC <= PC + 4;
            end
        end
    end
    export "DPI-C" task read_pc;
    task read_pc(output int data);
        data = PC;
    endtask
endmodule
