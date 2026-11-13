# npc/Makefile 注入 bug 练习清单

只覆盖**正确性验证的关键路径**：

- `make bin` / `make sim`：ysyxSoC 版仿真，接 nvboard，跑 RT-Thread、microbench、贪吃蛇；
- `make verilog`：生成交付用 `build/ysyx_26030092.sv`（RTL 流程）。

NPC 独立版（`make npc` / `npc-run`）、四值仿真（`sim-iverilog`）、网表仿真（`sim-iverilog-netlist`）、`lint` 都不在正确性验证路径里，不从这里出题。

练习规则：

- 每轮只注入 **一处** 增 / 删 / 改，只改 `npc/Makefile`，其它文件（RTL、csrc、nvboard）保持不动。
- 注入后不告知编号和位置；你在 `npc` 下用 `make` 复现、定位、修复。
- 修复后我核对：注入被移除、SoC 路径（`make bin` / `make sim IMG=...` / RT-Thread 启动）跑通；npc cpu-tests 只作附加回归。核对完把状态改成 `已用（第 N 轮）`。
- 练习时不要看 `git diff` / `git status`（等于看答案）。

| 编号 | 类别 | 位置 | 注入方式（一处） | 现象 | 正确写法 | 状态 |
|---|---|---|---|---|---|---|
| B01 | 语法 | `$(BIN)` 的 recipe | 某行行首 tab 改成 4 个空格 | 任何 `make` 都直接报 `Makefile:NN: *** 缺失分隔符` | 恢复 tab | 已用（第 30 轮） |
| B02 | 变量展开 | `INCFLAGS` 定义 | `INCFLAGS = ...` 改成 `INCFLAGS := ...` | 编译 `build/auto_bind.cpp` 报 `nvboard.h: 没有那个文件或目录`（`nvboard.mk` 在后面才往 `INC_PATH` 追加目录，`:=` 提前展开丢了它） | 用 `=` | 已用（第 3 轮） |
| B03 | 路径 | `INC_PATH` | 删掉 `INC_PATH += $(NPC_HOME)/include/generated` | 编译 csrc 报 `autoconf.h: No such file or directory`（两种仿真都编不出来） | 恢复该行 | 已用（第 1 轮） |
| B04 | 链接 | `$(BIN)` 的 verilator 命令 | 参数里的 `$(NVBOARD_ARCHIVE)` 删掉 | 链接期一堆 `undefined reference to nvboard_*`（nvboard 没链进来） | 保留 `$(NVBOARD_ARCHIVE)` | 未用 |
| B05 | 源文件 | `VSRCS` 的 `find` | `-name "*.v"` 改成 `-name "*.sv"` | verilator 报 `Cannot find file containing module: 'ysyxSoCFull'`（perip 和 SoC 顶层都没进源列表） | 改回 `*.v` | 已用（第 26 轮） |
| B06 | 顶层 | `$(BIN)` 的 verilator 命令 | `--top-module $(TOPNAME)` 改成 `--top-module $(DESIGN)` | 生成的 C++ 头是 `Vysyx_26030092.h`，`main.cpp` 找不到 `VysyxSoCFull.h`，编译直接失败 | 恢复 `$(TOPNAME)` | 已用（第 7 轮） |
| B07 | 生成 | `$(CORE)` 的 recipe | `cat $(SPLIT_DIR)/*.sv $(RESOURCES) > $@` 里删掉 `$(RESOURCES)` | 交付文件里缺 `PCBlackBox`/`perf` 等 resources，verilator 报缺模块（`ysyx_26030092_PCBlackBox`） | 恢复 `$(RESOURCES)` | 已用（第 31 轮） |
| B08 | nvboard | `$(SRC_AUTO_BIND)` 的 recipe | `... auto_pin_bind.py $^ $@` 改成 `... auto_pin_bind.py $@ $^` | 引脚绑定脚本参数反了：生成失败或 `auto_bind.cpp` 内容错乱，nvboard 编译/链接失败（贪吃蛇键盘、VGA 都受影响） | 恢复 `$^ $@` | 已用（第 11 轮） |
| B09 | 依赖 | `sim` 目标 | `sim: bin` 改成 `sim:` | 干净状态下 `make sim` 报 `build/ysyxSoCFull: No such file or directory`；只改源码也不会先重编，RT-Thread 跑的是旧程序 | 恢复 `sim: bin` | 已用（第 6 轮） |
| B10 | 链接 | `LDFLAGS` 定义 | `LDFLAGS += -lreadline -ldl` 改成 `LDFLAGS += -ldl` | 只在 `CONFIG_ENABLE_SDB=y` 时链接失败；当前 CI 配置 SDB 关，readline 调用被编掉，**无现象，作废** | — | 作废 |
| B11 | nvboard | `include $(NVBOARD_HOME)/scripts/nvboard.mk` | 删掉这一行 | `$(NVBOARD_ARCHIVE)` 为空、nvboard 目标也没了，编译 `auto_bind.cpp` 先报 `nvboard.h: No such file`（INC_PATH 也少了 nvboard 目录），其后才是链接错误 | 恢复 include | 已用（第 27 轮） |
| B12 | 依赖 | `$(SRC_AUTO_BIND)` 的依赖 | 去掉 `$(NXDC_FILES)` | 改了 `constr/top.nxdc` 后 `auto_bind.cpp` 不重新生成，引脚映射还是旧的（贪吃蛇键盘/VGA 表现异常，静默） | 恢复依赖 | 未用 |
| B13 | 依赖 | `$(CORE)` 的依赖 | 去掉 `$(CHISEL_SRC)` | 改了 scala 后 `make` 不重新 elaborate，仿真用旧 RTL（静默） | 恢复依赖 | 未用 |
| B14 | 生成 | `$(CORE)` 的 recipe | `> $@` 改成 `>> $@` | 第一次正常；触发重新生成后内容叠加（实测 313713 → 627426 字节），verilator 报 `Duplicate declaration of module: 'ysyx_26030092'` | 改回 `>` | 已用（第 32 轮） |
| B15 | 命令 | `$(BIN)` 的 verilator 命令 | 删掉 `--exe` | 只生成库不生成可执行文件：`make` 退出码 0 但没有 `build/ysyxSoCFull`，随后 `make sim` 报 `错误 127` | 恢复 `--exe` | 已用（第 29 轮） |
| B16 | 命令 | `sim` 的 recipe | `$(BUILD_DIR)/$(TOPNAME)` 改成 `$(BUILD_DIR)/npc_sim` | 跑的是 NPC 独立版（没有 nvboard、存储模型不同），RT-Thread / microbench 起不来 | 恢复 `$(TOPNAME)` | 已用（第 2 轮） |
| B17 | 源文件 | `$(BIN)` 的 verilator 命令 | 参数里 `$(CORE)` 改成 `$(NET_CORE)` | verilator 报找不到 `build/sim/ysyx_26030092_netlist_sim.sv`（只有跑过网表流程才有该文件） | 恢复 `$(CORE)` | 已用（第 5 轮） |
| B18 | 依赖 | `$(BIN)` 的依赖 | 去掉 `$(CORE)` | 改了 RTL 后 `make bin` 不重新 verilate，仿真跑旧核（静默） | 恢复依赖 | 未用 |
| B19 | 宏 | `INCFLAGS`/`CXXFLAGS` 里的 `-DTOP_NAME` | `-DTOP_NAME="\"V$(TOPNAME)\""` 改成 `V$(DESIGN)` | `engine.cpp` 里 `static TOP_NAME top;` 展开成 `Vysyx_26030092`，报 `‘Vysyx_26030092’ does not name a type`（类型名来自命令行宏） | 恢复 `$(TOPNAME)` | 已用（第 17 轮；首猜未中） |
| B20 | 路径 | `NXDC_FILES` | `constr/top.nxdc` 改成 `constr/old_top.nxdc` | `make: *** No rule to make target 'constr/old_top.nxdc'`（中文 locale 下是 `没有规则可制作目标`），SoC 版构建失败 | 恢复路径 | 已用（第 25 轮） |
| B21 | 源文件 | `$(BIN)` 的 verilator 命令 | 命令里的 `$(SOC_CSRCS)` 改成 `$(CSRCS)` | 链接期 `undefined reference to nvboard_bind_all_pins`（`auto_bind.cpp` 没参与编译） | 恢复 `$(SOC_CSRCS)` | 已用（第 12 轮，给了线索） |
| B22 | 生成 | `$(CORE)` 的 recipe | `--target-dir $(SPLIT_DIR)` 改成 `--target-dir out` | `cat build/split/*.sv` 匹配不到文件，报 `cat: build/split/*.sv: No such file`，`ysyx_26030092.sv` 生成失败 | 恢复 `$(SPLIT_DIR)` | 已用（第 9 轮） |
| B23 | 头文件路径 | `INC_PATH` | 删掉 `INC_PATH += $(CURDIR)/$(BUILD_DIR)` | 编译 `ebreak.cpp`/`perf.cpp` 报 `perf_names.h: No such file or directory`（它在 `build/` 下） | 恢复该行 | 已用（第 14 轮） |
| B24 | Verilator 参数 | `VERILATOR_CFLAGS` | 删掉 `-I$(YSYX_SoC_HOME)/perip/uart16550/rtl` | verilator 报 `Cannot find include file: 'uart_defines.v'`（uart 源码里的 `` `include `` 依赖这个路径） | 恢复该 `-I` | 已用（第 20 轮） |
| B25 | 生成 | `$(PERF_NAMES_H)` 的 recipe | `gen_perf_names.py $(BUILD_DIR) $@` 改成 `gen_perf_names.py $@ $(BUILD_DIR)` | 脚本把 `perf_names.h` 当成 build 目录去解析，报 `Error: top module 'ysyx_26030092' not found in build/perf_names.h`，`perf_names.h` 生成失败 | 恢复参数顺序 | 已用（第 8 轮） |
| B26 | Verilator 参数 | `VERILATOR_CFLAGS` | 删掉 `--build` | verilator 只生成 `obj_dir` 里的 Makefile 不编译，`make` 看起来成功却没有 `build/ysyxSoCFull`，紧接着 `make sim` 报找不到可执行文件 | 恢复 `--build` | 已用（第 16 轮） |
| B27 | 参数 | `$(BIN)` 的 verilator 命令 | `-CFLAGS "$(CXXFLAGS)"` 改成 `-CFLAGS "$(CXXFLAGS) -DNPC"` | SoC 构建里 `#ifdef NPC` 的 `npc-*.cpp` 被激活，链接期 `multiple definition of 'init_engine'`（和 SoC 版引擎重复） | 去掉 `-DNPC` | 已用（第 15 轮） |
| B28 | 参数 | `$(BIN)` 的 verilator 命令 | `--top-module` 拼成 `--top_module` | verilator 报 `Invalid option: --top_module... Suggested alternative: '-top-module'`，构建失败 | 恢复拼写 | 已用（第 21 轮） |
| B29 | 参数 | `$(BIN)` 的 verilator 命令 | `--Mdir` 拼成 `--mdir` | verilator 报 `Invalid option: --mdir... Suggested alternative: '-Mdir'`，构建失败 | 恢复拼写 | 已用（第 23 轮） |
| B30 | 参数 | `$(BIN)` 的 verilator 命令 | 源列表 `$(VSRCS)` 后面追加 `vsrc/npc_top.v` | `npc_top.v` 里也有一个 `module ysyxSoCFull`，verilator 报模块重定义 | 去掉追加 | 已用（第 13 轮） |
| B31 | 参数 | `sim` 的 recipe | `-e $(ELF)` 改成 `-E $(ELF)` | 引擎打印 `invalid option -- 'E'` 后 `exit 1`，make 报 `错误 1`，没有任何仿真输出 | 恢复 `-e` | 已用（第 10 轮） |
| B32 | 参数 | `$(BIN)` 的 verilator 命令 | `-O3` 改成 `-O` | ~~gcc 报 unrecognized option~~ 实测 g++ 接受 `-O`（等同 -O1），**无现象，作废** | — | 作废 |
| B33 | 源文件 | `RESOURCES` 的 `find` | `-name "*.v"` 改成 `-name "*.sv"` | `RESOURCES` 变空，交付 .sv 里缺 `PCBlackBox`/`Commit` 等模块，verilator 报缺模块 | 改回 `*.v` | 已用（第 19 轮） |
| B34 | 依赖 | `$(CORE)` 的依赖 | 去掉 `$(RESOURCES)` | 只改 `resources/*.v` 时 `make` 不重新拼 `ysyx_26030092.sv`，仿真用旧 RTL（静默） | 恢复依赖 | 未用 |
| B35 | 生成 | `$(PERF_NAMES_H)` 的 recipe | `gen_perf_names.py $(BUILD_DIR) $@` 删掉 `$@` | 脚本 `sys.argv[2]` 越界，抛 `IndexError`，`perf_names.h` 生成失败 | 恢复 `$@` | 已用（第 18 轮） |
| B36 | 参数 | `$(BIN)` 的 verilator 命令 | `-cc` 改成 `-sc` | `%Error: Need $SYSTEMC_INCLUDE in environment...`，没法按 SystemC 模式生成 | 恢复 `-cc` | 已用（第 28 轮） |
| B37 | 参数 | `$(BIN)` 的 verilator 命令 | `--timescale "1ns/1ns"` 改成 `--timescale "1ns/1ps"` | 时间单位与 tb/仿真预期不一致，波形和超时判断异常（仿真变慢或提前结束） | 恢复 `1ns/1ns` | 未用 |
| B38 | 依赖 | `$(BIN)` 的依赖 | 去掉 `$(NVBOARD_ARCHIVE)` | 改了 nvboard 源码后不会重新链接，仿真里 nvboard 行为是旧的（静默） | 恢复依赖 | 未用 |
| B39 | 源文件 | `CSRCS` 的 `find` | `-name "*.cpp"` 改成 `-name "*.cc"` | 没有任何 C++ 源码进编译，verilator 报找不到 `main`/一堆 `undefined reference` | 改回 `*.cpp` | 已用（第 24 轮） |
| B40 | 变量展开 | `CORE` 定义 | `CORE = $(BUILD_DIR)/ysyx_26030092.sv` 改成 `CORE := $(BUILD_DIR)/ysyx_26030092.sv` | 表面等价，但如果你后续改了 `BUILD_DIR`/依赖顺序，`CORE` 会指向旧路径（当前配置下不报错，属于埋雷型） | 用 `=` | 未用 |

## 备选（需要时替换进上表）

| 编号 | 类别 | 位置 | 注入方式（一处） | 现象 | 状态 |
|---|---|---|---|---|---|
| X01 | 输出路径 | `$(BIN)` 的 recipe | `-o $(CURDIR)/$(BIN)` 改成 `-o $(BIN)` | 可执行文件生成到 `build/obj_dir/build/...` 里，`make sim` 报找不到 `build/ysyxSoCFull` | 已用（第 22 轮） |
| X02 | 依赖 | `$(CORE)` 的依赖 | 去掉 `$(RESOURCES)` | 只改 `resources/*.v` 时 `make` 不重新拼 `ysyx_26030092.sv`，仿真用旧 RTL（静默） | 未用 |
| X03 | 依赖 | `$(BIN)` 的依赖 | 去掉 `$(VSRCS)` | 只改 perip 或 `ysyxSoCFull.v` 时不重新 verilate，仿真跑旧 SoC（静默） | 未用 |
| X04 | 目录 | `$(BIN)` 的 verilator 命令 | `--Mdir $(OBJ_DIR)` 改成 `--Mdir $(NPC_OBJ_DIR)` | SoC 版和 NPC 版共用输出目录，互相覆盖，构建报错或跑成另一版 | 未用 |
| X05 | 参数 | `sim` 的 recipe | 去掉 `$(if $(IMG),-i $(IMG))` | 没有指定镜像，内存是空的，复位后取指全错，很快卡到周期上限（已在 C++ 层加了“无镜像直接报错”的防护） | 已用（第 4 轮） |
| X06 | 依赖 | `$(PERF_NAMES_H)` 的依赖 | 去掉 `$(CORE)` | RTL 重新生成后 perf 名字表不更新，PMU 统计名字错乱（不影响程序功能，静默） | 未用 |

## 轮次记录

| 轮次 | 编号 | 现象 | 修法 |
|---|---|---|---|
| 1 | B03 | `csrc/difftest.cpp:8:10: fatal error: autoconf.h: 没有那个文件或目录` | 恢复 `INC_PATH += $(NPC_HOME)/include/generated/`（用户写成了带尾斜杠，等价） |
| 2 | B16 | `make sim` 跑的是 `build/npc_sim` | 恢复 `$(BUILD_DIR)/$(TOPNAME)` |
| 3 | B02 | `build/auto_bind.cpp:1:10: fatal error: nvboard.h` | 把 `include nvboard.mk` 提前到 `INCFLAGS` 之前（等价于恢复 `=`），事后已清理重复 include 并恢复 `INCFLAGS =` |
| 4 | X05 | `make sim` 不带镜像，空内存跑飞；在 C++ 层加了“无 `-i` 直接报错”的防护 | 恢复 `$(if $(IMG),-i $(IMG))` |
| 5 | B17 | `%Error: Cannot find file containing module: 'build/sim/ysyx_26030092_netlist_sim.sv'` | 恢复 `$(CORE)` |
| 6 | B09 | 干净状态直接 `make sim` 报 `build/ysyxSoCFull: 没有那个文件或目录` | 恢复 `sim: bin` |
| 7 | B06 | `build/auto_bind.cpp:2:10: fatal error: VysyxSoCFull.h`（生成的 mk 变成 `Vysyx_26030092.mk`） | 恢复 `--top-module $(TOPNAME)` |
| 8 | B25 | `Error: top module 'ysyx_26030092' not found in build/perf_names.h`（参数顺序反了） | 恢复 `gen_perf_names.py $(BUILD_DIR) $@` |
| 9 | B22 | `cat: 'build/split/*.sv': 没有那个文件或目录` | 恢复 `--target-dir $(SPLIT_DIR)` |
| 10 | B31 | `build/ysyxSoCFull: invalid option -- 'E'` + `make: *** [Makefile:71：sim] 错误 1` | 恢复 `-e $(ELF)` |
| 11 | B08 | `Error: Constraint file doesn't exist:`（`$@ $^` 顺序反了，把生成的 cpp 当约束文件） | 恢复 `$^ $@` |
| 12 | B21 | `undefined reference to nvboard_bind_all_pins(VysyxSoCFull*)`（`auto_bind.cpp` 没进编译） | 命令里 `$(CSRCS)` 恢复成 `$(SOC_CSRCS)` |
| 13 | B30 | `%Warning-MODDUP: vsrc/npc_top.v:1:8: Duplicate declaration of module: 'ysyxSoCFull'` | 去掉追加的 `vsrc/npc_top.v` |
| 14 | B23 | `csrc/perf.cpp:4:10: fatal error: perf_names.h: 没有那个文件或目录` | 恢复 `INC_PATH += $(CURDIR)/$(BUILD_DIR)` |
| 15 | B27 | 链接期重复定义（SoC 构建混入 `-DNPC`，`npc-*.cpp` 被激活） | 去掉 `-DNPC` |
| 16 | B26 | `make` 显示成功但没生成 `build/ysyxSoCFull`，随后 `make sim` 报找不到可执行文件 | 恢复 `--build` |
| 17 | B19 | `‘Vysyx_26030092’ does not name a type` + `nvboard_bind_all_pins 声明为 void`（`-DTOP_NAME` 用了 `$(DESIGN)`） | 恢复 `-DTOP_NAME="\"V$(TOPNAME)\""` |
| 18 | B35 | `IndexError: list index out of range`（`gen_perf_names.py` 少了输出参数） | 恢复 `$@` |
| 19 | B33 | `%Error-MODMISSING: ... Cannot find file containing module: 'ysyx_26030092_PerfCounterArray'` | `RESOURCES` 的 find 改回 `*.v` |
| 20 | B24 | `Cannot find include file: 'uart_defines.v'` + `` `UART_FIFO_WIDTH `` 未定义 | 恢复 `-I.../perip/uart16550/rtl` |
| 21 | B28 | `%Error: Invalid option: --top_module... Suggested alternative: '-top-module'` | 恢复 `--top-module` |
| 22 | X01 | `-o build/ysyxSoCFull` 相对 `obj_dir` 解析，链接失败、没有可执行文件 | 恢复 `-o $(CURDIR)/$(BIN)` |
| 23 | B29 | `%Error: Invalid option: --mdir... Suggested alternative: '-Mdir'` | 恢复 `--Mdir` |
| 24 | B39 | `undefined reference to 'main'`（`CSRCS` 的 find 匹配不到 `.cpp`） | 改回 `-name "*.cpp"` |
| 25 | B20 | `make: *** 没有规则可制作目标“constr/old_top.nxdc”`（`NXDC_FILES` 改名） | 恢复 `constr/top.nxdc` |
| 26 | B05 | `%Error-MODMISSING: ... Cannot find file containing module: 'sdram_top_axi' / 'psram_top_apb' / 'flash'` | `VSRCS` 的 find 改回 `*.v` |
| 27 | B11 | `build/auto_bind.cpp:1:10: fatal error: nvboard.h: 没有那个文件或目录` | 恢复 `include $(NVBOARD_HOME)/scripts/nvboard.mk` |
| 28 | B36 | `%Error: Need $SYSTEMC_INCLUDE in environment...`（`-sc`） | 恢复 `-cc` |
| 29 | B15 | `make` 退出 0 但无 `build/ysyxSoCFull`，`make sim` 报 `错误 127` | 恢复 `--exe` |
| 30 | B01 | `Makefile:61: *** 缺失分隔符`（tab 变空格） | 恢复 tab |
| 31 | B07 | `Cannot find module: 'ysyx_26030092_PCBlackBox'` / `PerfCounterArray` | `cat` 恢复 `$(RESOURCES)` |
| 32 | B14 | 第二次 `make` 后 CORE 从 313713 变成 627426 字节，`%Warning-MODDUP: Duplicate declaration of module: 'ysyx_26030092'` | `>>` 改回 `>` |

## 难度分组（后续优先抽「中 / 难」）

- **易**：B01、B05、B07、B10、B11、B15、B20、B28、B29、B36、B39、X01（报错信息基本直接指向问题）
- **中**：B04、B06、B08、B09、B14、B17、B19、B21、B22、B23、B24、B25、B26、B27、B30、B33、B35、X04、X05（需要看懂依赖关系或命令行）
- **难**：B02、B12、B13、B18、B37、B38、B40、X02、X03、X06（静默或不易察觉：需要自己触发重建或对比行为）
