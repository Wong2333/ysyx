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
| B01 | 语法 | `$(BIN)` 的 recipe | 某行行首 tab 改成 4 个空格 | 任何 `make` 都直接报 `Makefile:NN: *** 缺失分隔符` | 恢复 tab | 未用 |
| B02 | 变量展开 | `INCFLAGS` 定义 | `INCFLAGS = ...` 改成 `INCFLAGS := ...` | 编译 `build/auto_bind.cpp` 报 `nvboard.h: 没有那个文件或目录`（`nvboard.mk` 在后面才往 `INC_PATH` 追加目录，`:=` 提前展开丢了它） | 用 `=` | 已用（第 3 轮） |
| B03 | 路径 | `INC_PATH` | 删掉 `INC_PATH += $(NPC_HOME)/include/generated` | 编译 csrc 报 `autoconf.h: No such file or directory`（两种仿真都编不出来） | 恢复该行 | 已用（第 1 轮） |
| B04 | 链接 | `$(BIN)` 的 verilator 命令 | 参数里的 `$(NVBOARD_ARCHIVE)` 删掉 | 链接期一堆 `undefined reference to nvboard_*`（nvboard 没链进来） | 保留 `$(NVBOARD_ARCHIVE)` | 未用 |
| B05 | 源文件 | `VSRCS` 的 `find` | `-name "*.v"` 改成 `-name "*.sv"` | verilator 报 `Cannot find file containing module: 'ysyxSoCFull'`（perip 和 SoC 顶层都没进源列表） | 改回 `*.v` | 未用 |
| B06 | 顶层 | `$(BIN)` 的 verilator 命令 | `--top-module $(TOPNAME)` 改成 `--top-module $(DESIGN)` | 生成的 C++ 头是 `Vysyx_26030092.h`，`main.cpp` 找不到 `VysyxSoCFull.h`，编译直接失败 | 恢复 `$(TOPNAME)` | 已用（第 7 轮） |
| B07 | 生成 | `$(CORE)` 的 recipe | `cat $(SPLIT_DIR)/*.sv $(RESOURCES) > $@` 里删掉 `$(RESOURCES)` | 交付文件里缺 `PCBlackBox`/`perf` 等 resources，verilator 报缺模块（`ysyx_26030092_PCBlackBox`） | 恢复 `$(RESOURCES)` | 未用 |
| B08 | nvboard | `$(SRC_AUTO_BIND)` 的 recipe | `... auto_pin_bind.py $^ $@` 改成 `... auto_pin_bind.py $@ $^` | 引脚绑定脚本参数反了：生成失败或 `auto_bind.cpp` 内容错乱，nvboard 编译/链接失败（贪吃蛇键盘、VGA 都受影响） | 恢复 `$^ $@` | 未用 |
| B09 | 依赖 | `sim` 目标 | `sim: bin` 改成 `sim:` | 干净状态下 `make sim` 报 `build/ysyxSoCFull: No such file or directory`；只改源码也不会先重编，RT-Thread 跑的是旧程序 | 恢复 `sim: bin` | 已用（第 6 轮） |
| B10 | 链接 | `LDFLAGS` 定义 | `LDFLAGS += -lreadline -ldl` 改成 `LDFLAGS += -ldl` | 链接期 `undefined reference to 'readline'` / `'add_history'`（两种仿真都编不出来） | 恢复 `-lreadline` | 未用 |
| B11 | nvboard | `include $(NVBOARD_HOME)/scripts/nvboard.mk` | 删掉这一行 | `$(NVBOARD_ARCHIVE)` 为空、nvboard 目标也没了，链接期 `undefined reference to nvboard_*` | 恢复 include | 未用 |
| B12 | 依赖 | `$(SRC_AUTO_BIND)` 的依赖 | 去掉 `$(NXDC_FILES)` | 改了 `constr/top.nxdc` 后 `auto_bind.cpp` 不重新生成，引脚映射还是旧的（贪吃蛇键盘/VGA 表现异常，静默） | 恢复依赖 | 未用 |
| B13 | 依赖 | `$(CORE)` 的依赖 | 去掉 `$(CHISEL_SRC)` | 改了 scala 后 `make` 不重新 elaborate，仿真用旧 RTL（静默） | 恢复依赖 | 未用 |
| B14 | 生成 | `$(CORE)` 的 recipe | `> $@` 改成 `>> $@` | 每次叠加写入，`ysyx_26030092.sv` 里模块重复定义，verilator 报重复声明 | 改回 `>` | 未用 |
| B15 | 命令 | `$(BIN)` 的 verilator 命令 | 删掉 `--exe` | 只生成库不生成可执行文件，链接/运行失败（找不到 `main`） | 恢复 `--exe` | 未用 |
| B16 | 命令 | `sim` 的 recipe | `$(BUILD_DIR)/$(TOPNAME)` 改成 `$(BUILD_DIR)/npc_sim` | 跑的是 NPC 独立版（没有 nvboard、存储模型不同），RT-Thread / microbench 起不来 | 恢复 `$(TOPNAME)` | 已用（第 2 轮） |
| B17 | 源文件 | `$(BIN)` 的 verilator 命令 | 参数里 `$(CORE)` 改成 `$(NET_CORE)` | verilator 报找不到 `build/sim/ysyx_26030092_netlist_sim.sv`（只有跑过网表流程才有该文件） | 恢复 `$(CORE)` | 已用（第 5 轮） |
| B18 | 依赖 | `$(BIN)` 的依赖 | 去掉 `$(CORE)` | 改了 RTL 后 `make bin` 不重新 verilate，仿真跑旧核（静默） | 恢复依赖 | 未用 |
| B19 | 宏 | `INCFLAGS`/`CXXFLAGS` 里的 `-DTOP_NAME` | `-DTOP_NAME="\"V$(TOPNAME)\""` 改成 `V$(DESIGN)` | C++ 里 `VysyxSoCFull.h` 找不到，`main.cpp` 编译失败 | 恢复 `$(TOPNAME)` | 未用 |
| B20 | 路径 | `NXDC_FILES` | `constr/top.nxdc` 改成 `constr/old_top.nxdc` | `make: *** No rule to make target 'constr/old_top.nxdc'`，SoC 版构建失败 | 恢复路径 | 未用 |
| B21 | 源文件 | `$(BIN)` 的 verilator 命令 | 命令里的 `$(SOC_CSRCS)` 改成 `$(CSRCS)` | 链接期 `undefined reference to nvboard_bind_all_pins`（`auto_bind.cpp` 没参与编译） | 恢复 `$(SOC_CSRCS)` | 未用 |
| B22 | 生成 | `$(CORE)` 的 recipe | `--target-dir $(SPLIT_DIR)` 改成 `--target-dir out` | `cat build/split/*.sv` 匹配不到文件，报 `cat: build/split/*.sv: No such file`，`ysyx_26030092.sv` 生成失败 | 恢复 `$(SPLIT_DIR)` | 已用（第 9 轮） |
| B23 | 头文件路径 | `INC_PATH` | 删掉 `INC_PATH += $(CURDIR)/$(BUILD_DIR)` | 编译 `ebreak.cpp`/`perf.cpp` 报 `perf_names.h: No such file or directory`（它在 `build/` 下） | 恢复该行 | 未用 |
| B24 | Verilator 参数 | `VERILATOR_CFLAGS` | 删掉 `-I$(YSYX_SoC_HOME)/perip/uart16550/rtl` | verilator 报 `Cannot find include file: 'uart_defines.v'`（uart 源码里的 `` `include `` 依赖这个路径） | 恢复该 `-I` | 未用 |
| B25 | 生成 | `$(PERF_NAMES_H)` 的 recipe | `gen_perf_names.py $(BUILD_DIR) $@` 改成 `gen_perf_names.py $@ $(BUILD_DIR)` | 脚本把 `perf_names.h` 当成 build 目录去解析，报 `Error: top module 'ysyx_26030092' not found in build/perf_names.h`，`perf_names.h` 生成失败 | 恢复参数顺序 | 已用（第 8 轮） |
| B26 | Verilator 参数 | `VERILATOR_CFLAGS` | 删掉 `--build` | verilator 只生成 `obj_dir` 里的 Makefile 不编译，`make` 看起来成功却没有 `build/ysyxSoCFull`，紧接着 `make sim` 报找不到可执行文件 | 恢复 `--build` | 未用 |
| B27 | 参数 | `$(BIN)` 的 verilator 命令 | `-CFLAGS "$(CXXFLAGS)"` 改成 `-CFLAGS "$(CXXFLAGS) -DNPC"` | SoC 构建里 `#ifdef NPC` 的 `npc-*.cpp` 被激活，链接期 `multiple definition of 'init_engine'`（和 SoC 版引擎重复） | 去掉 `-DNPC` | 未用 |
| B28 | 参数 | `$(BIN)` 的 verilator 命令 | `--top-module` 拼成 `--top_module` | verilator 报未知选项、直接退出，构建失败 | 恢复拼写 | 未用 |
| B29 | 参数 | `$(BIN)` 的 verilator 命令 | `--Mdir` 拼成 `--mdir` | verilator 报未知选项，构建失败 | 恢复拼写 | 未用 |
| B30 | 参数 | `$(BIN)` 的 verilator 命令 | 源列表 `$(VSRCS)` 后面追加 `vsrc/npc_top.v` | `npc_top.v` 里也有一个 `module ysyxSoCFull`，verilator 报模块重定义 | 去掉追加 | 未用 |
| B31 | 参数 | `sim` 的 recipe | `-e $(ELF)` 改成 `-E $(ELF)` | 引擎不认识 `-E`，打印 usage 后 `exit 1` | 恢复 `-e` | 未用 |
| B32 | 参数 | `$(BIN)` 的 verilator 命令 | `-O3` 改成 `-O` | 编译阶段 gcc 报 `unrecognized command-line option '-O'`，构建失败 | 恢复 `-O3` | 未用 |

## 备选（需要时替换进上表）

| 编号 | 类别 | 位置 | 注入方式（一处） | 现象 |
|---|---|---|---|---|
| X01 | 输出路径 | `$(BIN)` 的 recipe | `-o $(CURDIR)/$(BIN)` 改成 `-o $(BIN)` | 可执行文件生成到 `build/obj_dir/build/...` 里，`make sim` 报找不到 `build/ysyxSoCFull` |
| X02 | 依赖 | `$(CORE)` 的依赖 | 去掉 `$(RESOURCES)` | 只改 `resources/*.v` 时 `make` 不重新拼 `ysyx_26030092.sv`，仿真用旧 RTL（静默） |
| X03 | 依赖 | `$(BIN)` 的依赖 | 去掉 `$(VSRCS)` | 只改 perip 或 `ysyxSoCFull.v` 时不重新 verilate，仿真跑旧 SoC（静默） |
| X04 | 目录 | `$(BIN)` 的 verilator 命令 | `--Mdir $(OBJ_DIR)` 改成 `--Mdir $(NPC_OBJ_DIR)` | SoC 版和 NPC 版共用输出目录，互相覆盖，构建报错或跑成另一版 |
| X05 | 参数 | `sim` 的 recipe | 去掉 `$(if $(IMG),-i $(IMG))` | 没有指定镜像，内存是空的，复位后取指全错，很快卡到周期上限（已在 C++ 层加了“无镜像直接报错”的防护） | 已用（第 4 轮） |
| X06 | 依赖 | `$(PERF_NAMES_H)` 的依赖 | 去掉 `$(CORE)` | RTL 重新生成后 perf 名字表不更新，PMU 统计名字错乱（不影响程序功能，静默） |

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

## 难度分组（后续优先抽「中 / 难」）

- **易**：B01、B05、B07、B10、B11、B15、B20、B28、B29、B31、B32、X01（报错信息基本直接指向问题）
- **中**：B04、B06、B08、B09、B14、B17、B19、B21、B22、B23、B24、B25、B26、B27、B30、X04、X05（需要看懂依赖关系或命令行）
- **难**：B02、B12、B13、B18、X02、X03、X06（静默错误：需要自己触发重建或对比行为才能发现）
