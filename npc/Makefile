TOPNAME = Top
NXDC_FILES = constr/top.nxdc
INC_PATH ?= $(NPC_HOME)/csrc/include
INC_PATH += $(NPC_HOME)/tools/capstone/repo/include
KCONFIG_INCLUDE = csrc/include/autoconfig.h
KCONFIG_VERILOG_INCLUDE = resources/include/autoconfig.svh

VERILATOR = verilator
VERILATOR_CFLAGS += -MMD --build -cc -CFLAGS -ggdb -LDFLAGS -ggdb \
				-O1 --x-assign fast --x-initial fast --noassert -Wall -Wno-UNUSEDSIGNAL

BUILD_DIR = ./build
OBJ_DIR = $(BUILD_DIR)/obj_dir
BIN = $(BUILD_DIR)/$(TOPNAME)
Top = $(BUILD_DIR)/$(TOPNAME).sv

default: 
	make bin

# constraint file
SRC_AUTO_BIND = $(abspath $(BUILD_DIR)/auto_bind.cpp)
$(SRC_AUTO_BIND): $(NXDC_FILES)
	python3 $(NVBOARD_HOME)/scripts/auto_pin_bind.py $^ $@

# project source
VSRCS = $(shell find $(abspath ./vsrc) -name "*.v")
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
bin: 
	make verilog
	make $(BIN)
$(BIN): $(KCONFIG_INCLUDE) $(KCONFIG_VERILOG_INCLUDE) $(RESOURCES) ./$(BUILD_DIR)/*.sv $(CSRCS) $(VSRCS) $(NVBOARD_ARCHIVE)
	@rm -rf $(OBJ_DIR)
	$(VERILATOR) $(VERILATOR_CFLAGS) \
		--top-module $(TOPNAME) $(RESOURCES) ./$(BUILD_DIR)/*.sv $(CSRCS) $(VSRCS) $(NVBOARD_ARCHIVE) \
		$(addprefix -CFLAGS , $(CXXFLAGS)) $(addprefix -LDFLAGS , $(LDFLAGS)) \
		--Mdir $(OBJ_DIR) --exe -o $(abspath $(BIN)) \
		-Iresources/include

test:
	mill -i test

verilog: $(Top)
$(Top): $(CHISEL_SRC) $(RESOURCES)
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

.PHONY: bin test verilog help reformat checkformat clean

sim: 
	make bin
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
	$(BUILD_DIR)/$(TOPNAME) $(if $(IMG),-i $(IMG)) $(if $(DIFF_REF),-d $(DIFF_REF))

-include ../Makefile

DESIGN = top

# 指定 RTL 文件（可以使用通配符，也可以显式列出）
RTL_FILES = $(wildcard build/*.sv)

# (可选) 如果有自己的约束文件
# SDC_FILE = ./constraint.sdc

# (可选) 指定不同的时钟频率
CLK_FREQ_MHZ = 500

include /home/wjm/yosys-sta/Makefile.common


$(KCONFIG_INCLUDE): .config
	python scripts/kconfig.py \
		--kconfig-file Kconfig \
		--config-out .config \
		--header-out csrc/include/autoconfig.h \
		--kconfig-list-out log.txt \
		--configs-in .config

$(KCONFIG_VERILOG_INCLUDE): .config
	python scripts/kconfig.py \
		--kconfig-file Kconfig \
		--config-out .config \
		--kconfig-list-out log.txt \
		--configs-in .config \
		--verilog-header resources/include/autoconfig.svh
.config:
	menuconfig
menuconfig:
	menuconfig