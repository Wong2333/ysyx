# rt-thread-am 补丁

存放对 `rt-thread-am` 的本地修改，`git format-patch origin/master` 生成。

## `0001-npc-platform.patch`

让 RT-Thread 能在 `riscv32e-npc` 平台上跑起来。

### 1. npc 专用链接脚本（核心问题）

`bsp/abstract-machine/extra.ld` 原本是给 ysyxSoC 写的：`.data.extra` 用
`> sdram AT> flash`（LMA 在 flash、VMA 在 sdram，靠 boot 拷贝）。而 npc 平台的
`abstract-machine/scripts/linker.ld` 里没有 `sdram`/`flash` 这两个 MEMORY 段，
段属性被忽略后 `.data.extra` 会落到**地址 0**：

```
[16] .data.extra  PROGBITS  00000000   size 0x3360
     __rt_init_start = 0x168   __rt_init_end = 0x1a4   ← 都在这个段里
```

于是 `rt_components_board_init` 里 `lw a5, 0(s0)`（`s0 = 364 = 0x16C`）读到 0，
`jalr a5` 跳到 0 取指得到 `0x00000000` → 非法指令（`mcause=2`、`mepc=0`）。

- 新增 `bsp/abstract-machine/extra-npc.ld`：段收集内容与 `extra.ld` 相同，只去掉
  `> sdram AT> flash` / `> sdram` 段属性，让段跟着 `.data` 落在 0x80000000 附近
- `bsp/abstract-machine/Makefile` 按 `$(ARCH)` 选择：
  - `riscv32e-ysyxsoc` → `extra.ld`（SoC 流程不受影响）
  - 其它（npc）→ `extra-npc.ld`

### 2. 其它本地修改

- `src/context.c`：实现 `EVENT_YIELD` 的上下文切换和 `rt_hw_context_switch_to`
- `src/init.c`：初始化流程微调
- `src/uart.c`：控制台读入用内置命令脚本，脚本耗尽后返回 -1
- npc 平台没有串口输入，`AM_UART_RX` 的桩在 `abstract-machine/am/src/riscv/npc/ioe.c`
  （已随主仓库提交）

## 验证

```
# npc 版：.data.extra 落在 0x8006b5dc（改前是 0x0）
riscv64-linux-gnu-readelf -S build/rtthread-riscv32e-npc.elf | grep data.extra

# 跑起来后：msh 启动，memcheck / utest_list / am_hello 全部执行，无 panic
```
