TOPNAME = ysyxSoCFull
NXDC_FILES = constr/top.nxdc
INC_PATH ?= $(NPC_HOME)/csrc/include
INC_PATH += $(NPC_HOME)/tools/capstone/repo/include
KCONFIG_INCLUDE = csrc/include/autoconfig.h
KCONFIG_VERILOG_INCLUDE = resources/include/autoconfig.svh

VERILATOR = verilator
VERILATOR_CFLAGS += --threads 1 -MMD --build -cc -CFLAGS -ggdb -LDFLAGS -ggdb \
				-O3 --x-assign fast --x-initial fast --noassert \
				--timescale "1ns/1ns" --no-timing --autoflush 

VERILATOR_INC_PATH = $(YSYX_SoC_HOME)/perip/uart16550/rtl $(YSYX_SoC_HOME)/perip/spi/rtl

VERILATOR_CFLAGS += $(addprefix -I, $(VERILATOR_INC_PATH))

-include .config
ifeq ($(CONFIG_ENABLE_FST_TRACE),y)
VERILATOR_CFLAGS += --trace-fst
endif

BUILD_DIR = ./build
OBJ_DIR = $(BUILD_DIR)/obj_dir
BIN = $(BUILD_DIR)/$(TOPNAME)
SPLIT_DIR = $(BUILD_DIR)/split
# 流片/CI 要求：所有 CPU 模块合并到一个文件，模块名统一加 ysyx_26030092_ 前缀
CORE = $(BUILD_DIR)/ysyx_26030092.sv
PERF_NAMES_H = $(BUILD_DIR)/perf_names.h

default: 
	make bin

# constraint file
SRC_AUTO_BIND = $(abspath $(BUILD_DIR)/auto_bind.cpp)
$(SRC_AUTO_BIND): $(NXDC_FILES)
	python3 $(NVBOARD_HOME)/scripts/auto_pin_bind.py $^ $@

# project source
VSRCS = $(shell find $(abspath ./vsrc) -name "*.v")
VSRCS += $(shell find $(abspath $(YSYX_SoC_HOME)/perip) -name "*.v")
VSRCS += $(YSYX_SoC_HOME)/build/ysyxSoCFull.v
VERILATOR_CFLAGS += $(addprefix -v , $(VSRCS))

CSRCS = $(shell find $(abspath ./csrc) -name "*.c" -or -name "*.cc" -or -name "*.cpp")
CSRCS += $(SRC_AUTO_BIND)
CHISEL_SRC = $(shell find $(abspath ./src) -name "*.scala")
RESOURCES = $(shell find $(abspath ./resources) -name "*.v")
# rules for NVBoard
include $(NVBOARD_HOME)/scripts/nvboard.mk

# rules for verilator
INCFLAGS = $(addprefix -I, $(INC_PATH)) -I$(abspath $(BUILD_DIR))
CXXFLAGS += $(INCFLAGS) -DTOP_NAME="\"V$(TOPNAME)\""
LDFLAGS += -lreadline -ldl
IMG=
DIFF_REF=
ELF=
bin: 
	make verilog
	make $(BIN)
$(BIN): $(KCONFIG_INCLUDE) $(KCONFIG_VERILOG_INCLUDE) $(CORE) $(PERF_NAMES_H) $(CSRCS) $(VSRCS) $(NVBOARD_ARCHIVE)
	@rm -rf $(OBJ_DIR)
	$(VERILATOR) $(VERILATOR_CFLAGS) \
		--top-module $(TOPNAME) $(CORE) $(CSRCS) $(NVBOARD_ARCHIVE) \
		$(addprefix -CFLAGS , $(CXXFLAGS)) $(addprefix -LDFLAGS , $(LDFLAGS)) \
		--Mdir $(OBJ_DIR) --exe -o $(abspath $(BIN)) \
		-Iresources/include

test:
	mill -i test

verilog: $(CORE) $(PERF_NAMES_H)

# firtool 按模块拆分输出，这里合并成单文件，并给未加前缀的模块补上 ysyx_26030092_
UNPREFIXED_MODS = BPU|icache|icacheMainPipe|icacheRefillUnit|cache_8x128|ram_2x97|tag_array_8x25|Commit|EbreakBlackBox|InvalidInstructionBlackBox|mtrace|PCBlackBox|PerfCounterArray|RegBlackBox

$(CORE): $(CHISEL_SRC) $(RESOURCES)
	$(call git_commit, "generate verilog")
	mkdir -p $(SPLIT_DIR)
	mill -i runMain Elaborate --target-dir $(SPLIT_DIR)
	cat $(SPLIT_DIR)/*.sv > $@.tmp
	sed -i -E 's/\b($(UNPREFIXED_MODS))\b/ysyx_26030092_\1/g' $@.tmp
	# 手写资源的 DPI 函数名不能跟着改（C++ 侧按原名提供），只改 module 声明
	for f in $(RESOURCES); do \
	    sed -E 's/^([[:space:]]*module[[:space:]]+)($(UNPREFIXED_MODS))([[:space:](])/\1ysyx_26030092_\2\3/' $$f >> $@.tmp; done
	mv $@.tmp $@

# 从生成的 Verilog 提取 PerfCounterArray 实例路径（名称唯一来源：RTL 的 PerfCounter 声明）
$(PERF_NAMES_H): $(CORE) scripts/gen_perf_names.py
	python3 scripts/gen_perf_names.py $(BUILD_DIR) $@

help:
	mill -i runMain Elaborate --help

reformat:
	mill -i __.reformat

checkformat:
	mill -i __.checkFormat

bsp:
	mill -i mill.bsp.BSP/install

idea:
	mill -i mill.idea.GenIdea/idea

clean:
	-rm -rf $(BUILD_DIR)
	-rm -rf out
rebuild:
	make clean
	make

.PHONY: bin test verilog sim-iverilog sim-iverilog-netlist help reformat checkformat clean perf lint

# 流片前静态检查（讲义要求）：只豁免 DECLFILENAME（与逻辑无关）和 UNUSEDSIGNAL（未用端口/位）
lint: $(CORE)
	verilator --lint-only -Wall -Wno-DECLFILENAME -Wno-UNUSEDSIGNAL \
		--top-module $(DESIGN) -Iresources/include $(CORE)
	echo "lint OK"

# ================= 四值仿真（iverilog）=================
# CI 约定：make -C npc sim-iverilog IMG=xxx.bin，程序第一条指令在 0x80000000
IVERILOG ?= iverilog
VVP      ?= vvp
SIM_DIR  = $(BUILD_DIR)/sim
SIM_HEX  = $(SIM_DIR)/img.hex
SIM_INC  = $(SIM_DIR)/include

$(SIM_INC)/autoconfig.svh: $(KCONFIG_VERILOG_INCLUDE)
	@mkdir -p $(SIM_INC)
	python3 scripts/sim_autoconfig.py $< $@

$(SIM_HEX): FORCE
	@test -n "$(IMG)" || (echo "用法: make sim-iverilog IMG=xxx.bin" && exit 1)
	@mkdir -p $(SIM_DIR)
	python3 scripts/bin2hex.py $(IMG) $@
FORCE:

# iverilog 不支持 DPI-C：用 -DSYNTHESIS 去掉所有 DPI/黑盒行为；
# PC 复位值通过替换后的 include 目录改成 0x80000000
SIM_CYCLES ?= 2000000
# 编译产物做成真实目标：换镜像跑的时候不必重新编译（scripts/run_tests.sh 依赖这一点）
$(SIM_DIR)/tb.vvp: $(CORE) $(SIM_INC)/autoconfig.svh sim/tb_npc.v sim/axi_mem.v
	@mkdir -p $(SIM_DIR)
	$(IVERILOG) -g2012 -DSYNTHESIS -D__ICARUS__ \
		-I$(SIM_INC) -Iresources/include -s tb_npc \
		-o $@ $(CORE) sim/axi_mem.v sim/tb_npc.v

sim-iverilog: $(SIM_HEX) $(SIM_DIR)/tb.vvp
	$(VVP) $(SIM_DIR)/tb.vvp +HEX=$(abspath $(SIM_HEX)) +CYCLES=$(SIM_CYCLES)

# 网表仿真：NETLIST=<yosys 综合出的网表> CELLS=<标准单元行为模型>
#   CI 约定：make -C npc sim-iverilog-netlist IMG=xxx NETLIST=yyy CELLS=zzz
#   传了 NETLIST 就直接用 CI 给的那份网表；没传才自己综合（本地调试方便）
CELLS ?= /home/wjm/yosys-sta/pdk/nangate45/sim/cells.v
NET_TB = $(SIM_DIR)/tb_netlist.v
NETLIST_SIM_DIR = $(SIM_DIR)/netlist
NETLIST_SIM_V   = $(NETLIST_SIM_DIR)/ysyx_26030092.netlist.v

ifeq ($(strip $(NETLIST)),)
  NETLIST_USE = $(NETLIST_SIM_V)
else
  NETLIST_USE = $(abspath $(NETLIST))
endif

# 用 0x80000000 起始地址的那份 autoconfig.svh 重新综合，供网表仿真使用
$(NETLIST_SIM_V): $(CORE) $(SIM_INC)/autoconfig.svh
	@mkdir -p $(NETLIST_SIM_DIR)
	echo tcl $(SCRIPT_DIR)/yosys.tcl $(DESIGN) $(PDK_NAME) \"$(CORE)\" $@ | \
	  VERILOG_INCLUDE_DIRS=$(abspath $(SIM_INC)):$(abspath resources/include) \
	  yosys -g -l $(NETLIST_SIM_DIR)/yosys.log -s -

$(NET_TB): $(NETLIST_USE)
	@mkdir -p $(SIM_DIR)
	python3 scripts/gen_netlist_tb.py $(NETLIST_USE) $(NET_TB)

$(SIM_DIR)/tb_net.vvp: $(NETLIST_USE) $(CELLS) sim/axi_mem.v $(NET_TB)
	@mkdir -p $(SIM_DIR)
	$(IVERILOG) -g2012 -DSYNTHESIS -D__ICARUS__ -DNETLIST_SIM \
		-I$(SIM_INC) -Iresources/include -s tb_netlist \
		-o $@ $(NETLIST_USE) $(CELLS) sim/axi_mem.v $(NET_TB)

sim-iverilog-netlist: $(SIM_HEX) $(SIM_DIR)/tb_net.vvp
	$(VVP) $(SIM_DIR)/tb_net.vvp +HEX=$(abspath $(SIM_HEX)) +CYCLES=$(SIM_CYCLES)


sim:
	make bin
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
	$(BUILD_DIR)/$(TOPNAME) $(if $(IMG),-i $(IMG)) $(if $(DIFF_REF),-d $(DIFF_REF)) $(if $(ELF),-e $(ELF))

# 性能评估：跑 microbench，把 PMU 计数器 + 综合面积/时序（make sta）记到 perf/ 下
#   make perf                  # mainargs=test（默认）
#   make perf PERF_ARGS=train  # 跑 train 规模
#   make perf PERF_STA=0       # 跳过 make sta（RTL 没变时它本来也只是空转）
PERF_BENCH ?= $(abspath ../am-kernels/benchmarks/microbench)
PERF_ARCH ?= riscv32e-ysyxsoc
PERF_ARGS ?= test
PERF_STA ?= 1
perf:
	PERF_STA=$(PERF_STA) tools/perf.sh -a $(PERF_ARGS) -n $(PERF_BENCH)

-include ../Makefile

DESIGN = ysyx_26030092

RTL_FILES = $(CORE)

export VERILOG_INCLUDE_DIRS = $(abspath resources/include)

export CLK_PORT_NAME = clock


CLK_FREQ_MHZ = 800

include /home/wjm/yosys-sta/Makefile.common

# 注意：必须在 include Makefile.common 之后覆盖
PDK_NAME = nangate45


$(KCONFIG_INCLUDE): .config
	python3 scripts/kconfig.py \
		--kconfig-file Kconfig \
		--config-out .config \
		--header-out csrc/include/autoconfig.h \
		--kconfig-list-out log.txt \
		--configs-in .config

$(KCONFIG_VERILOG_INCLUDE): .config
	python3 scripts/kconfig.py \
		--kconfig-file Kconfig \
		--config-out .config \
		--kconfig-list-out log.txt \
		--configs-in .config \
		--verilog-header resources/include/autoconfig.svh
.config:
	python -m menuconfig Kconfig
menuconfig:
	python -m menuconfig Kconfig
