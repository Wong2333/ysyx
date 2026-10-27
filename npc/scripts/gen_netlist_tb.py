#!/usr/bin/env python3
# 为"综合网表"生成一个专用 testbench：
#   yosys 综合后顶层端口被 bit-blast 成 io_master_awaddr_0_ 这样的形式，
#   这里从网表的端口声明直接生成例化语句，把逐位端口接回向量信号，
#   既不改网表、也不加 wrap 模块。
#
# 用法: gen_netlist_tb.py <netlist.v> <out_tb.v>
import re
import sys

SRC = "ysyx_26030092"


def parse_ports(path):
    text = open(path).read()
    m = re.search(r'^module\s+' + SRC + r'\s*\(', text, re.M)
    if not m:
        raise SystemExit("网表里找不到 module " + SRC)
    names = [n.strip() for n in text[m.end():text.index(");", m.end())].split(",") if n.strip()]
    # 方向声明
    dirs = {}
    for line in text[text.index(");", m.end()):].split("\n"):
        line = line.strip().rstrip(";")
        for d in ("input", "output", "inout"):
            if line.startswith(d + " "):
                nm = line.split()[-1]
                dirs[nm] = d
                break
        if line.startswith("assign") or line.startswith("endmodule"):
            break
    return [(n, dirs.get(n, "input")) for n in names]


def vec_name(port):
    """io_master_awaddr_3_ -> ('io_master_awaddr', 3)；单 bit 端口返回 (port, None)"""
    m = re.match(r'^(.*)_(\d+)_$', port)
    if m:
        return m.group(1), int(m.group(2))
    return port, None


def main():
    net, out = sys.argv[1], sys.argv[2]
    ports = parse_ports(net)

    # 综合时如果没加 -flatten，IFU 层次还在，就能直接探内部信号；
    # 加了 -flatten 就只有顶层端口可看，结束判据退化成"AXI 静止"。
    hier = "_IFU_u_out_bits_pc_0_" in open(net).read()

    # 1) 归组，算出每个向量信号的位宽
    groups = {}          # base -> width
    singles = []         # 单 bit 端口名
    for (p, d) in ports:
        base, idx = vec_name(p)
        if idx is None:
            singles.append((p, d))
        else:
            groups[base] = max(groups.get(base, 0), idx + 1)

    sig = {}             # port -> 连接的 tb 信号表达式
    decl = []            # tb 里的信号声明
    for base, w in sorted(groups.items()):
        vn = "m_" + base.replace("io_master_", "").replace("io_slave_", "s_")
        decl.append("  wire [%d:0] %s;" % (w - 1, vn))
        for i in range(w):
            sig["%s_%d_" % (base, i)] = "%s[%d]" % (vn, i)
    for (p, d) in singles:
        vn = "m_" + p.replace("io_master_", "").replace("io_slave_", "s_")
        if not any(vn in x for x in decl):
            decl.append("  wire %s;" % vn)
        sig[p] = vn

    # 2) 例化语句：slave 侧输入接 0，输出留空
    conn = ["    .clock(clock)", "    .reset(reset)"]
    for (p, d) in ports:
        if p in ("clock", "reset"):
            continue
        if p == "io_interrupt":          # 顶层中断输入，仿真里恒为 0（RTL tb 里也是这样接的）
            conn.append("    .%s(1'b0)" % p)
            continue
        if p.startswith("io_slave_"):
            if d == "input":
                conn.append("    .%s(1'b0)" % p)
            continue
        conn.append("    .%s(%s)" % (p, sig[p]))

    # 3) 结束判据：ebreak 之后 PC 跳到 mtvec（复位值 0），于是 IFU 会去取 0x0 的指令
    #    —— 程序正常执行绝不会从地址 0 取指，用这个当"程序结束"的判据。
    #    这个信号只用到顶层 AXI 端口，扁不扁平都能用；
    #    保留层次的网表再额外看 PC 是否变 X（双保险）。
    stop = """  // ebreak → 异常 → PC := mtvec(复位值 0) → IFU 发 AR 到 0x0，据此判定程序结束
  wire fetch_zero = m_arvalid & m_arready & (m_araddr == 32'd0);"""
    if hier:
        stop += """
  wire pc_x = (^dut._IFU_u_out_bits_pc_0_ === 1'bx);"""
    else:
        stop += """
  wire pc_x = 1'b0;          // 扁平网表看不到内部 PC，只能靠 fetch_zero"""
    stop_cond = """    if ((cycles > 200) && (fetch_zero || pc_x)) begin
      $display("[netlist] EBREAK: 程序结束, cycles=%0d", cycles);
      $finish;
    end"""

    # 4) 生成 tb
    txt = """// 自动生成：综合网表专用 testbench（不改网表、不加 wrap）
// 端口逐位连接由 gen_netlist_tb.py 生成（hier=@@HIER@@）
`timescale 1ns/1ps
module tb_netlist;
  reg clock = 1'b0;
  reg reset = 1'b1;
  always #0.625 clock = ~clock;          // 800MHz

@@DECL@@

  @@SRC@@ dut(
@@CONN@@
  );

  // 存储器（AXI 从机行为模型）
  axi_mem mem(
    .clock(clock), .reset(reset),
    .awready(m_awready), .awvalid(m_awvalid), .awaddr(m_awaddr), .awid(m_awid),
    .awlen(m_awlen), .awsize(m_awsize), .awburst(m_awburst),
    .wready(m_wready), .wvalid(m_wvalid), .wdata(m_wdata), .wstrb(m_wstrb), .wlast(m_wlast),
    .bready(m_bready), .bvalid(m_bvalid), .bresp(m_bresp), .bid(m_bid),
    .arready(m_arready), .arvalid(m_arvalid), .araddr(m_araddr), .arid(m_arid),
    .arlen(m_arlen), .arsize(m_arsize), .arburst(m_arburst),
    .rready(m_rready), .rvalid(m_rvalid), .rresp(m_rresp), .rdata(m_rdata),
    .rid(m_rid), .rlast(m_rlast)
  );

  integer cycles = 0;
  integer idle = 0;
  integer max_cycles = 2000000;

  reg [1023:0] hexfile;
  initial begin
    if ($value$plusargs("HEX=%s", hexfile)) $readmemh(hexfile, mem.mem);
`ifdef SIM_HEX
    else $readmemh(`SIM_HEX, mem.mem);
`endif
    void'($value$plusargs("CYCLES=%d", max_cycles));
    $display("[netlist] 已装载镜像，开始仿真");
    #1 reset = 1'b0;
  end

  // 前 10 笔 AXI 事务打印出来，便于和 RTL 仿真逐笔对比
  integer n_ar = 0, n_aw = 0, n_r = 0, n_b = 0;
  always @(posedge clock) begin
    if (m_arvalid & m_arready & (n_ar < 10)) begin
      $display("[axi] AR #%0d addr=0x%08x len=%0d", n_ar, m_araddr, m_arlen); n_ar <= n_ar + 1;
    end
    if (m_rvalid & m_rready & (n_r < 10)) begin
      $display("[axi]  R #%0d data=0x%08x last=%0d", n_r, m_rdata, m_rlast); n_r <= n_r + 1;
    end
    if (m_awvalid & m_awready & (n_aw < 10)) begin
      $display("[axi] AW #%0d addr=0x%08x", n_aw, m_awaddr); n_aw <= n_aw + 1;
    end
    if (m_bvalid & m_bready & (n_b < 10)) begin
      $display("[axi]  B #%0d resp=%0d", n_b, m_bresp); n_b <= n_b + 1;
    end
  end

@@STOP@@

  wire axi_active = (m_arvalid & m_arready) | (m_awvalid & m_awready) |
                    (m_wvalid & m_wready) | (m_rvalid & m_rready) | (m_bvalid & m_bready);
  always @(posedge clock) begin
    cycles <= cycles + 1;
    idle   <= axi_active ? 0 : idle + 1;
@@STOPCOND@@
    if ((idle > 20000) && (cycles > 2000)) begin
      $display("[netlist] AXI 静止 20000 拍（程序已停），总 %0d 拍", cycles);
      $finish;
    end
    if (cycles > max_cycles) begin
      $display("[netlist] TIMEOUT: 达到周期上限 %0d", cycles);
      $finish;
    end
  end
endmodule
"""
    txt = (txt.replace("@@DECL@@", "\n".join(decl))
              .replace("@@SRC@@", SRC)
              .replace("@@CONN@@", ",\n".join(conn))
              .replace("@@HIER@@", "1" if hier else "0")
              .replace("@@STOP@@", stop)
              .replace("@@STOPCOND@@", stop_cond))
    open(out, "w").write(txt)
    print("gen_netlist_tb: %d 端口 -> %s (hier=%d)" % (len(ports), out, hier))


main()
