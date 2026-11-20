# ysyxSoC 补丁（按文件拆分）

基准：**固定在 `BASE`**（`df38a4d9`，origin/ysyx6 的前一版；上游后来前进了，补丁仍按这个基准验证），
只包含本人（wangjingmin）的修改，**每个被修改的文件一个 patch**。

> 一键拉取 + 打补丁：`./patch/setup.sh`（会先把仓库 checkout 到 `BASE` 再逐个 apply）。

- 应用：在 ysyxSoC 仓库根目录执行
  ```bash
  for p in /path/to/ysyx-workbench/patch/ysyxSoC/*.patch; do git apply "$p"; done
  ```
- 未包含：`patch/firtool/` 下的 firtool 二进制（由 `make verilog` 里的
  `patch/update-firtool.sh` 自动下载/打补丁）。

| 文件 | 说明 |
|---|---|
| 01-gitignore.patch | 忽略构建产物 |
| 02-bitrev.patch ~ 09-sdram_top_axi.patch | perip 下的 bitrev / psram / sdram Verilog 修改 |
| 10-APBDelayer.patch、11-AXI4Delayer.patch | 两个 APB/AXI 延时模块 |
| 12-CPU.patch | CPU 黑盒声明 |
| 13-GPIO.patch ~ 17-VGA.patch | GPIO / Keyboard / SDRAM 模型 / SPI(XIP) / VGA |
| 18-SoC.patch、19-Top.patch | SoC 顶层连线与平台配置 |
