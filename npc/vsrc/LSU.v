module LSU(
    input valid,
    input wen,
    input [3:0]mem_ctl,//1:LW 2:LB 3:SW 4:SB
    input [31:0]src1,
    input [31:0]src2,
    input [31:0]ALU_out,
    output reg[31:0]mem_out
);
    import "DPI-C" context function int pmem_read(input int raddr);
    import "DPI-C" context function void pmem_write(input int waddr, input int wdata, input byte wmask);
    wire [31:0]raddr = valid?ALU_out:0;
    wire [31:0]waddr = valid?ALU_out:0;
    reg [3:0]mask;
    reg [31:0]rdata;
    reg [31:0]wdata;

    always @(*) begin
        mask = 4'b0;
        mem_out = 32'b0;
        wdata = 32'b0;
        if(mem_ctl == 1)begin
            mem_out = rdata;
        end
        else if(mem_ctl == 2)begin
            case (raddr[1:0])
                2'b00:mem_out = {{24{rdata[7]}},rdata[7:0]};
                2'b01:mem_out = {{24{rdata[15]}},rdata[15:8]};
                2'b10:mem_out = {{24{rdata[23]}},rdata[23:16]};
                2'b11:mem_out = {{24{rdata[31]}},rdata[31:24]};
            endcase
        end
        else if(mem_ctl == 3)begin
            case (raddr[1:0])
                2'b00:mem_out = {24'b0,rdata[7:0]};
                2'b01:mem_out = {24'b0,rdata[15:8]};
                2'b10:mem_out = {24'b0,rdata[23:16]};
                2'b11:mem_out = {24'b0,rdata[31:24]};
                
            endcase
        end
        else if(mem_ctl == 4)begin
            mask = 4'b1111;
            wdata = src2;
        end
        else if(mem_ctl == 5)begin
            case (waddr[1:0])
                2'b00:begin mask = 4'b0001; wdata = src2; end
                2'b01:begin mask = 4'b0010; wdata = src2 << 8; end
                2'b10:begin mask = 4'b0100; wdata = src2 << 16; end
                2'b11:begin mask = 4'b1000; wdata = src2 << 24; end
            endcase
        end
    end

    always @(*) begin
        if (valid) begin // 有读写请求时

            if (wen) begin // 有写请求时
                pmem_write(waddr, wdata, {4'b0,mask});
                rdata = 0;
                $display("LSU:write %h to %h",wdata,waddr);
            end
            else begin
                rdata = pmem_read(raddr);
                $display("LSU:read %h from %h",rdata,raddr);
            end
        end
        else begin
            rdata = 0;
        end
    end
endmodule
