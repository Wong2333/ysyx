TOPNAME = ysyxSoCFull
NXDC_FILES = constr/top.nxdc
INC_PATH ?= $(NPC_HOME)/csrc/include
INC_PATH += $(NPC_HOME)/tools/capstone/repo/include
KCONFIG_INCLUDE = csrc/include/autoconfig.h
KCONFIG_VERILOG_INCLUDE = resources/include/autoconfig.svh

VERILATOR = verilator
VERILATOR_CFLAGS += --threads 1 -MMD --build -cc -CFLAGS -ggdb -LDFLAGS -ggdb \
				-O1 --x-assign fast --x-initial fast --noassert \
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
CORE = $(BUILD_DIR)/ysyx_26030092.sv

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
INCFLAGS = $(addprefix -I, $(INC_PATH))
CXXFLAGS += $(INCFLAGS) -DTOP_NAME="\"V$(TOPNAME)\""
LDFLAGS += -lreadline -ldl
IMG=
DIFF_REF=
ELF=
bin: 
	make verilog
	make $(BIN)
$(BIN): $(KCONFIG_INCLUDE) $(KCONFIG_VERILOG_INCLUDE) $(RESOURCES) ./$(BUILD_DIR)/*.sv $(CSRCS) $(VSRCS) $(NVBOARD_ARCHIVE)
	@rm -rf $(OBJ_DIR)
	$(VERILATOR) $(VERILATOR_CFLAGS) \
		--top-module $(TOPNAME) $(RESOURCES) ./$(BUILD_DIR)/*.sv $(CSRCS) $(NVBOARD_ARCHIVE) \
		$(addprefix -CFLAGS , $(CXXFLAGS)) $(addprefix -LDFLAGS , $(LDFLAGS)) \
		--Mdir $(OBJ_DIR) --exe -o $(abspath $(BIN)) \
		-Iresources/include

test:
	mill -i test

verilog: $(CORE)
$(CORE): $(CHISEL_SRC) $(RESOURCES)
	$(call git_commit, "generate verilog")
	mkdir -p $(BUILD_DIR)
	mill -i runMain Elaborate --target-dir $(BUILD_DIR)

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

.PHONY: bin test verilog help reformat checkformat clean

sim: 
	make bin
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
	$(BUILD_DIR)/$(TOPNAME) $(if $(IMG),-i $(IMG)) $(if $(DIFF_REF),-d $(DIFF_REF)) $(if $(ELF),-e $(ELF))

-include ../Makefile

DESIGN = ysyxSoCASIC

# 指定 RTL 文件（可以使用通配符，也可以显式列出）
RTL_FILES = $(wildcard build/*.sv) $(YSYX_SoC_HOME)/build/ysyxSoCFull.v \
	$(RESOURCES) \
	$(YSYX_SoC_HOME)/perip/amba/apb_delayer.v \
	$(wildcard $(YSYX_SoC_HOME)/perip/uart16550/rtl/*.v) \
	$(wildcard $(YSYX_SoC_HOME)/perip/spi/rtl/*.v) \
	$(YSYX_SoC_HOME)/perip/psram/psram_top_apb.v \
	$(YSYX_SoC_HOME)/perip/psram/efabless/EF_PSRAM_CTRL_wb.v \
	$(YSYX_SoC_HOME)/perip/psram/efabless/EF_PSRAM_CTRL.v \
	$(YSYX_SoC_HOME)/perip/sdram/sdram_top_apb.v \
	$(YSYX_SoC_HOME)/perip/sdram/core_sdram_axi4/sdram_axi_core.v

# yosys 读取 RTL 时需要的 include 路径（resources 里有 `include "autoconfig.svh"）
export VERILOG_INCLUDE_DIRS = $(abspath resources/include)

# 顶层时钟端口名（Chisel 默认叫 clock）
export CLK_PORT_NAME = clock

# (可选) 如果有自己的约束文件
# SDC_FILE = ./constraint.sdc

# (可选) 指定不同的时钟频率
CLK_FREQ_MHZ = 500

include /home/wjm/yosys-sta/Makefile.common


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
