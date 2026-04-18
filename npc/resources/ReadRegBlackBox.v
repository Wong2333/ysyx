module moduleName (
    input [31:0]addr,
    output [31:0]data
);
    export "DPI-C" task read_reg;
    task read_reg(input int addr, output int data);
        data = rf[addr[4:0]];
    endtask
endmodule
