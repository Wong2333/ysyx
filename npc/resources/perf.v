module PerfCounterArray(
    input io_clock,
    input io_reset,
    input [14:0] io_events
);
`ifndef SYNTHESIS
    reg [63:0] counters [0:14];
    integer i;

    always @(posedge io_clock) begin
        if (io_reset) begin
            for (i = 0; i < 15; i = i + 1) counters[i] <= 64'h0;
        end
        else begin
            for (i = 0; i < 15; i = i + 1) begin
                if (io_events[i]) counters[i] <= counters[i] + 64'h1;
            end
        end
    end

    export "DPI-C" task perf_read;
    task perf_read(input int idx, output longint data);
        data = counters[idx];
    endtask
`endif
endmodule
