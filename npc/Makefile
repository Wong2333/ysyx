default: bin

# 基本配置
TOPNAME = ysyxSoCFull
NXDC_FILES = constr/top.nxdc

BUILD_DIR = build
OBJ_DIR = $(BUILD_DIR)/obj_dir
BIN = $(BUILD_DIR)/$(TOPNAME)
SPLIT_DIR = $(BUILD_DIR)/split
CORE = $(BUILD_DIR)/ysyx_26030092.sv
PERF_NAMES_H = $(BUILD_DIR)/perf_names.h

INC_PATH ?= $(NPC_HOME)/csrc/include
INC_PATH += $(NPC_HOME)/tools/capstone/include
INC_PATH += $(NPC_HOME)/include/generated/
INC_PATH += $(CURDIR)/$(BUILD_DIR)/
KCONFIG_INCLUDE = include/generated/autoconf.h

INCFLAGS = $(addprefix -I,$(INC_PATH))
CXXFLAGS += $(INCFLAGS) -DTOP_NAME="\"V$(TOPNAME)\"" #-g -fsanitize=address
LDFLAGS += -lreadline -ldl #-g -fsanitize=address

# kconfig
-include include/config/auto.conf

$(KCONFIG_INCLUDE): .config
	$(Q)$(MAKE) $(silent) -C $(KCONFIG_PATH) NAME=conf
	$(Q)$(CONF) $(silent) --syncconfig $(Kconfig)

# Verilator 公共参数 
VERILATOR = verilator
VERILATOR_CFLAGS += --build -cc -O3 --noassert \
				--timescale "1ns/1ns" --no-timing --autoflush

VERILATOR_CFLAGS += -I$(YSYX_SoC_HOME)/perip/spi/rtl -I$(YSYX_SoC_HOME)/perip/uart16550/rtl

ifeq ($(CONFIG_ENABLE_FST_TRACE),y)
VERILATOR_CFLAGS += --trace-fst
endif

# 源文件 
NPC_VSRCS = vsrc/npc_top.v vsrc/npc_axi_mem.v
VSRCS = $(shell find $(YSYX_SoC_HOME)/perip -name "*.v")
VSRCS += $(YSYX_SoC_HOME)/build/ysyxSoCFull.v

CSRCS = $(shell find $(CURDIR)/csrc -name "*.cpp")
CHISEL_SRC = $(shell find $(CURDIR)/src -name "*.scala")
RESOURCES = $(shell find $(CURDIR)/resources -name "*.v")

include $(NVBOARD_HOME)/scripts/nvboard.mk
SRC_AUTO_BIND = $(CURDIR)/$(BUILD_DIR)/auto_bind.cpp
SOC_CSRCS = $(CSRCS) $(SRC_AUTO_BIND)
$(SRC_AUTO_BIND): $(NXDC_FILES)
	python3 $(NVBOARD_HOME)/scripts/auto_pin_bind.py $^ $@

# SoC
bin: $(BIN)

$(BIN): $(KCONFIG_INCLUDE) $(CORE) $(VSRCS) $(PERF_NAMES_H) $(SOC_CSRCS) $(NVBOARD_ARCHIVE)
	@rm -rf $(OBJ_DIR)
	$(VERILATOR) $(VERILATOR_CFLAGS) $(VSRCS) \
		--top-module $(TOPNAME) $(CORE) $(SOC_CSRCS) $(NVBOARD_ARCHIVE)\
		-CFLAGS "$(CXXFLAGS)" -LDFLAGS "$(LDFLAGS)" \
		--Mdir $(OBJ_DIR) --exe -o  $(CURDIR)/$(BIN) \
		-Iresources/include

sim: bin
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
	$(BUILD_DIR)/ysyxSoCFull $(if $(IMG),-i $(IMG)) $(if $(DIFF_REF),-d $(DIFF_REF)) $(if $(ELF),-e $(ELF))

# NPC
NPC_OBJ_DIR = $(BUILD_DIR)/obj_dir_npc
NPC_BIN = $(BUILD_DIR)/npc_sim

npc: $(NPC_BIN)

$(NPC_BIN): $(KCONFIG_INCLUDE) $(CORE) $(PERF_NAMES_H) $(NPC_VSRCS) $(CSRCS)
	@rm -rf $(NPC_OBJ_DIR)
	$(VERILATOR) $(VERILATOR_CFLAGS) -DNPC --top-module $(TOPNAME) \
		$(CORE) $(NPC_VSRCS) $(CSRCS) \
		-CFLAGS "$(CXXFLAGS) -DNPC" -LDFLAGS "$(LDFLAGS)" \
		--Mdir $(NPC_OBJ_DIR) --exe -o $(CURDIR)/$(NPC_BIN) \
		-Iresources/include

npc-run: npc
	$(NPC_BIN) $(if $(IMG),-i $(IMG)) $(if $(ELF),-e $(ELF)) $(if $(DIFF_REF),-d $(DIFF_REF))

# Verilog 生成 
verilog: $(CORE) $(PERF_NAMES_H)

$(CORE): $(CHISEL_SRC) $(RESOURCES)
	$(call git_commit, "generate verilog")
	rm -rf $(SPLIT_DIR)
	mkdir -p $(SPLIT_DIR)
	mill -i runMain Elaborate --target-dir $(SPLIT_DIR)
	cat $(SPLIT_DIR)/*.sv $(RESOURCES) > $@

$(PERF_NAMES_H): $(CORE) scripts/gen_perf_names.py
	python3 scripts/gen_perf_names.py $(BUILD_DIR) $@

# 四值仿真
IVERILOG ?= iverilog
VVP ?= vvp
SIM_DIR = $(BUILD_DIR)/sim
SIM_HEX = $(SIM_DIR)/img.hex
SIM_CYCLES ?= 2000000
IVERILOG_FLAGS = -g2012 -DSYNTHESIS -DNPC

$(SIM_HEX): FORCE
	@test -n "$(IMG)" || (echo "用法: make sim-iverilog IMG=xxx.bin" && exit 1)
	@mkdir -p $(SIM_DIR)
	python3 scripts/bin2hex.py $(IMG) $@
FORCE:

$(SIM_DIR)/tb.vvp: $(CORE) sim/tb_npc.v sim/axi_mem.v
	@mkdir -p $(SIM_DIR)
	$(IVERILOG) $(IVERILOG_FLAGS) -s tb_npc -o $@ $(CORE) sim/axi_mem.v sim/tb_npc.v

sim-iverilog: $(SIM_HEX) $(SIM_DIR)/tb.vvp
	$(VVP) $(SIM_DIR)/tb.vvp +HEX=$(CURDIR)/$(SIM_HEX) +CYCLES=$(SIM_CYCLES)

# 网表仿真
YOSYS_STA_HOME ?= /home/wjm/yosys-sta
CELLS ?= $(YOSYS_STA_HOME)/pdk/nangate45/sim/cells.v
NET_TB = $(SIM_DIR)/tb_netlist.v
NETLIST_SIM_DIR = $(SIM_DIR)/netlist
NETLIST_SIM_V = $(NETLIST_SIM_DIR)/ysyx_26030092.netlist.v
NET_CORE = $(SIM_DIR)/ysyx_26030092_netlist_sim.sv

ifeq ($(NETLIST),)
NETLIST_USE = $(NETLIST_SIM_V)
else
NETLIST_USE = $(abspath $(NETLIST))
endif

$(NET_CORE): $(CORE)
	@mkdir -p $(SIM_DIR)
	{ echo '`define NETLIST_SIM'; cat $<; } > $@

$(NETLIST_SIM_V): $(NET_CORE)
	@mkdir -p $(NETLIST_SIM_DIR)
	echo tcl $(SCRIPT_DIR)/yosys.tcl $(DESIGN) $(PDK_NAME) \"$(NET_CORE)\" $@ | \
	  yosys -g -l $(NETLIST_SIM_DIR)/yosys.log -s -

$(NET_TB): $(NETLIST_USE) scripts/gen_netlist_tb.py
	@mkdir -p $(SIM_DIR)
	python3 scripts/gen_netlist_tb.py $(NETLIST_USE) $(NET_TB)

$(SIM_DIR)/tb_net.vvp: $(NETLIST_USE) $(CELLS) sim/axi_mem.v $(NET_TB)
	@mkdir -p $(SIM_DIR)
	$(IVERILOG) $(IVERILOG_FLAGS) -DNETLIST_SIM -s tb_netlist -o $@ $(NETLIST_USE) $(CELLS) sim/axi_mem.v $(NET_TB)

sim-iverilog-netlist: $(SIM_HEX) $(SIM_DIR)/tb_net.vvp
	$(VVP) $(SIM_DIR)/tb_net.vvp +HEX=$(CURDIR)/$(SIM_HEX) +CYCLES=$(SIM_CYCLES)

# 静态检查
lint: $(CORE)
	verilator --lint-only -Wall -Wno-DECLFILENAME -Wno-UNUSEDSIGNAL \
		--top-module $(DESIGN) -Iresources/include $(CORE)
	echo "lint OK"

# 常用工具目标
test:
	mill -i test

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
	$(MAKE) clean
	$(MAKE)

PERF_BENCH ?= $(CURDIR)/../am-kernels/benchmarks/microbench
PERF_ARGS ?= test
PERF_STA ?= 1
perf:
	PERF_STA=$(PERF_STA) tools/perf.sh -a $(PERF_ARGS) -n $(PERF_BENCH)

# yosys-sta（面积和时序）
DESIGN = ysyx_26030092
RTL_FILES = $(CORE)
export VERILOG_INCLUDE_DIRS = $(CURDIR)/resources/include
export CLK_PORT_NAME = clock
CLK_FREQ_MHZ = 800

-include $(YOSYS_STA_HOME)/Makefile.common

PDK_NAME = nangate45

-include ../Makefile

include $(NPC_HOME)/scripts/config.mk

.PHONY: default bin npc npc-run sim test verilog sim-iverilog sim-iverilog-netlist lint help reformat checkformat bsp idea clean rebuild perf FORCE
