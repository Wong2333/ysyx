# rt-thread-am 补丁（按文件拆分）

基准：`origin/master`，只包含本人的修改，**每个被修改的文件一个 patch**。

- 应用：在 rt-thread-am 仓库根目录执行
  ```bash
  for p in /path/to/ysyx-workbench/patch/rt-thread-am/*.patch; do git apply "$p"; done
  ```

## 内容

- `01-extra.ld.patch`：ysyxSoC 用的原始链接脚本（含 `.data.extra > sdram AT> flash`）
- `02-extra-npc.ld.patch`：npc 专用链接脚本，去掉 `> sdram AT> flash`，让 `.data.extra`
  跟着 `.data` 落在 0x80000000 附近（原脚本 nop 平台没有 sdram/flash，段会落到地址 0）
- `03-Makefile.patch`：按 `$(ARCH)` 在 `extra.ld` / `extra-npc.ld` 之间选择
- `04-context.c.patch`：`EVENT_YIELD` 上下文切换、`rt_hw_context_switch_to`
- `05-init.c.patch`：初始化流程微调
- `06-uart.c.patch`：控制台读入用内置命令脚本，脚本耗尽后走 `AM_UART_RX`

验证：npc 版启动后 `msh` 起来，`memcheck`/`utest_list`/`am_hello` 正常执行。
