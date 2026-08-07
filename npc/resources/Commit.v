module Commit(
    input [31:0] pc,
    input valid,
    input clock,
    input reset,
    input mem_commit,
    input [31:0] mem_addr
);
`ifndef SYNTHESIS
    import "DPI-C" context function void commit(input int pc);
    always @(*) begin
        if(valid) begin
            commit(pc);
        end
    end

    // 锁存提交指令的访存信息，供 difftest 判断是否跳过 NEMU
    reg mem_valid_r;
    reg [31:0] mem_addr_r;
    always @(posedge clock) begin
        if (reset) begin
            mem_valid_r <= 1'b0;
            mem_addr_r  <= 32'h0;
        end
        else if (mem_commit) begin
            mem_valid_r <= 1'b1;
            mem_addr_r  <= mem_addr;
        end
    end

    export "DPI-C" task difftest_mem_info;
    task difftest_mem_info(output int valid, output int addr);
        valid = mem_valid_r;
        addr = mem_addr_r;
        mem_valid_r = 1'b0;  // 一次性读取：读取后清除
    endtask
`endif
endmodule
