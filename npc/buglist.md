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
| B02 | 变量展开 | `INCFLAGS` 定义 | `INCFLAGS = ...` 改成 `INCFLAGS := ...` | 编译 `build/auto_bind.cpp` 报 `nvboard.h: 没有那个文件或目录`（`nvboard.mk` 在后面才往 `INC_PATH` 追加目录，`:=` 提前展开丢了它） | 用 `=` | 未用 |
| B03 | 路径 | `INC_PATH` | 删掉 `INC_PATH += $(NPC_HOME)/include/generated` | 编译 csrc 报 `autoconf.h: No such file or directory`（两种仿真都编不出来） | 恢复该行 | 未用 |
| B04 | 链接 | `$(BIN)` 的 verilator 命令 | 参数里的 `$(NVBOARD_ARCHIVE)` 删掉 | 链接期一堆 `undefined reference to nvboard_*`（nvboard 没链进来） | 保留 `$(NVBOARD_ARCHIVE)` | 未用 |
| B05 | 源文件 | `VSRCS` 的 `find` | `-name "*.v"` 改成 `-name "*.sv"` | verilator 报 `Cannot find file containing module: 'ysyxSoCFull'`（perip 和 SoC 顶层都没进源列表） | 改回 `*.v` | 未用 |
| B06 | 顶层 | `$(BIN)` 的 verilator 命令 | `--top-module $(TOPNAME)` 改成 `--top-module $(DESIGN)` | 生成的 C++ 头是 `Vysyx_26030092.h`，`main.cpp` 找不到 `VysyxSoCFull.h`，编译直接失败 | 恢复 `$(TOPNAME)` | 未用 |
| B07 | 生成 | `$(CORE)` 的 recipe | `cat $(SPLIT_DIR)/*.sv $(RESOURCES) > $@` 里删掉 `$(RESOURCES)` | 交付文件里缺 `PCBlackBox`/`perf` 等 resources，verilator 报缺模块（`ysyx_26030092_PCBlackBox`） | 恢复 `$(RESOURCES)` | 未用 |
| B08 | nvboard | `$(SRC_AUTO_BIND)` 的 recipe | `... auto_pin_bind.py $^ $@` 改成 `... auto_pin_bind.py $@ $^` | 引脚绑定脚本参数反了：生成失败或 `auto_bind.cpp` 内容错乱，nvboard 编译/链接失败（贪吃蛇键盘、VGA 都受影响） | 恢复 `$^ $@` | 未用 |
| B09 | 依赖 | `sim` 目标 | `sim: bin` 改成 `sim:` | 干净状态下 `make sim` 报 `build/ysyxSoCFull: No such file or directory`；只改源码也不会先重编，RT-Thread 跑的是旧程序 | 恢复 `sim: bin` | 未用 |
| B10 | 链接 | `LDFLAGS` 定义 | `LDFLAGS += -lreadline -ldl` 改成 `LDFLAGS += -ldl` | 链接期 `undefined reference to 'readline'` / `'add_history'`（两种仿真都编不出来） | 恢复 `-lreadline` | 未用 |
| B11 | nvboard | `include $(NVBOARD_HOME)/scripts/nvboard.mk` | 删掉这一行 | `$(NVBOARD_ARCHIVE)` 为空、nvboard 目标也没了，链接期 `undefined reference to nvboard_*` | 恢复 include | 未用 |
| B12 | 依赖 | `$(SRC_AUTO_BIND)` 的依赖 | 去掉 `$(NXDC_FILES)` | 改了 `constr/top.nxdc` 后 `auto_bind.cpp` 不重新生成，引脚映射还是旧的（贪吃蛇键盘/VGA 表现异常，静默） | 恢复依赖 | 未用 |
| B13 | 依赖 | `$(CORE)` 的依赖 | 去掉 `$(CHISEL_SRC)` | 改了 scala 后 `make` 不重新 elaborate，仿真用旧 RTL（静默） | 恢复依赖 | 未用 |
| B14 | 生成 | `$(CORE)` 的 recipe | `> $@` 改成 `>> $@` | 每次叠加写入，`ysyx_26030092.sv` 里模块重复定义，verilator 报重复声明 | 改回 `>` | 未用 |
| B15 | 命令 | `$(BIN)` 的 verilator 命令 | 删掉 `--exe` | 只生成库不生成可执行文件，链接/运行失败（找不到 `main`） | 恢复 `--exe` | 未用 |
| B16 | 命令 | `sim` 的 recipe | `$(BUILD_DIR)/$(TOPNAME)` 改成 `$(BUILD_DIR)/npc_sim` | 跑的是 NPC 独立版（没有 nvboard、存储模型不同），RT-Thread / microbench 起不来 | 恢复 `$(TOPNAME)` | 未用 |
| B17 | 源文件 | `$(BIN)` 的 verilator 命令 | 参数里 `$(CORE)` 改成 `$(NET_CORE)` | verilator 报找不到 `build/sim/ysyx_26030092_netlist_sim.sv`（只有跑过网表流程才有该文件） | 恢复 `$(CORE)` | 未用 |
| B18 | 依赖 | `$(BIN)` 的依赖 | 去掉 `$(CORE)` | 改了 RTL 后 `make bin` 不重新 verilate，仿真跑旧核（静默） | 恢复依赖 | 未用 |
| B19 | 宏 | `INCFLAGS`/`CXXFLAGS` 里的 `-DTOP_NAME` | `-DTOP_NAME="\"V$(TOPNAME)\""` 改成 `V$(DESIGN)` | C++ 里 `VysyxSoCFull.h` 找不到，`main.cpp` 编译失败 | 恢复 `$(TOPNAME)` | 未用 |
| B20 | 路径 | `NXDC_FILES` | `constr/top.nxdc` 改成 `constr/old_top.nxdc` | `make: *** No rule to make target 'constr/old_top.nxdc'`，SoC 版构建失败 | 恢复路径 | 未用 |

## 备选（需要时替换进上表）

| 编号 | 类别 | 位置 | 注入方式（一处） | 现象 |
|---|---|---|---|---|
| X01 | 输出路径 | `$(BIN)` 的 recipe | `-o $(CURDIR)/$(BIN)` 改成 `-o $(BIN)` | 可执行文件生成到 `build/obj_dir/build/...` 里，`make sim` 报找不到 `build/ysyxSoCFull` |
| X02 | 依赖 | `$(CORE)` 的依赖 | 去掉 `$(RESOURCES)` | 只改 `resources/*.v` 时 `make` 不重新拼 `ysyx_26030092.sv`，仿真用旧 RTL（静默） |
| X03 | 依赖 | `$(BIN)` 的依赖 | 去掉 `$(VSRCS)` | 只改 perip 或 `ysyxSoCFull.v` 时不重新 verilate，仿真跑旧 SoC（静默） |
| X04 | 目录 | `$(BIN)` 的 verilator 命令 | `--Mdir $(OBJ_DIR)` 改成 `--Mdir $(NPC_OBJ_DIR)` | SoC 版和 NPC 版共用输出目录，互相覆盖，构建报错或跑成另一版 |
| X05 | 参数 | `sim` 的 recipe | 去掉 `$(if $(IMG),-i $(IMG))` | 没有指定镜像，内存是空的，复位后取指全错，很快卡到周期上限 |
| X06 | 依赖 | `$(PERF_NAMES_H)` 的依赖 | 去掉 `$(CORE)` | RTL 重新生成后 perf 名字表不更新，PMU 统计名字错乱（不影响程序功能，静默） |
