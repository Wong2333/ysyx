module ReadRegBlackBox (
    input [31:0]addr
);
    export "DPI-C" task read_reg;
    task read_reg(input int addr, output int data);
        data = rf[addr[4:0]];
    endtask
endmodule
