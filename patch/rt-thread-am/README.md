# rt-thread-am 补丁

存放对 `rt-thread-am` 的本地修改补丁。

## `0001-npc-linker.patch`

**问题**：`bsp/abstract-machine/extra.ld` 是给 ysyxSoC 版本写的，里面 `.data.extra`
用了 `> sdram AT> flash`（LMA 在 flash、VMA 在 sdram，靠 boot 拷贝）。npc 平台的
`abstract-machine/scripts/linker.ld` 里没有 `sdram`/`flash` 这两个 MEMORY 段，段属性
被忽略后 `.data.extra` 落到**地址 0**，于是 `__rt_init_start = 0x168`，程序运行到
`rt_components_board_init` 时从 0x168 读初始化函数表读到 0，`jalr a5` 跳到 0 取指，
触发非法指令（`mcause = 2`、`mepc = 0`）。

**修改**：

- 新增 `bsp/abstract-machine/extra-npc.ld`：段收集内容和 `extra.ld` 完全一样，
  只是去掉 `> sdram AT> flash` / `> sdram` 段属性，让它跟着 `.data` 落在 0x80000000 附近
- `bsp/abstract-machine/Makefile`：按 `$(ARCH)` 选链接脚本
  - `riscv32e-ysyxSoC` → `extra.ld`（原样保留，SoC 流程不受影响）
  - 其它（npc 等） → `extra-npc.ld`

**验证**：

```
# npc 版：.data.extra 落到 0x8006b5dc（改前是 0x0）
riscv64-linux-gnu-readelf -S build/rtthread-riscv32e-npc.elf | grep data.extra
# ysyxSoC 版仍使用 extra.ld
make ARCH=riscv32e-ysyxSoC -n | grep -c extra.ld    # -> 1
```

改完之后 RT-Thread 在 npc 上能正常启动、跑 msh、执行 `memcheck` / `utest_list` / `am_hello`。

**应用方式**：

```bash
cd <rt-thread-am>
git apply /path/to/0001-npc-linker.patch
```
