// difftest 辅助：锁存"提交指令的访存信息"，供 C 侧通过 DPI-C 读取
// mem_commit: 提交脉冲（EXU 提交且为访存指令）
// mem_addr:   访存地址（LSU 锁存，执行时刻的真实地址）
module DifftestHelper(
    input io_clock,
    input io_reset,
    input io_mem_commit,
    input [31:0] io_mem_addr
);
`ifndef SYNTHESIS
    reg mem_valid_r;
    reg [31:0] mem_addr_r;

    always @(posedge io_clock) begin
        if (io_reset) begin
            mem_valid_r <= 1'b0;
            mem_addr_r  <= 32'h0;
        end
        else if (io_mem_commit) begin
            mem_valid_r <= 1'b1;
            mem_addr_r  <= io_mem_addr;
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
