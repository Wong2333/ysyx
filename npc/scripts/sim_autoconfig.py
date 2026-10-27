#!/usr/bin/env python3
# 为 iverilog 仿真生成一份 autoconfig.svh：起始地址改成 0x80000000（NPC 单独运行）
import sys
src, dst = sys.argv[1], sys.argv[2]
s = open(src).read().replace("32'h30000000", "32'h80000000")
open(dst, 'w').write(s)
print(f"generated {dst} (START_ADDR = 0x80000000)")
