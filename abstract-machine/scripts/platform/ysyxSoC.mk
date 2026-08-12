AM_SRCS := riscv/ysyxSoC/start.S \
           riscv/ysyxSoC/trm.c \
           riscv/ysyxSoC/ioe.c \
           riscv/ysyxSoC/timer.c \
           riscv/ysyxSoC/input.c \
           riscv/ysyxSoC/cte.c \
           riscv/ysyxSoC/trap.S \
           riscv/ysyxSoC/bootloader.c \
           riscv/ysyxSoC/uart.c \
           riscv/ysyxSoC/gpu.c \
           platform/dummy/vme.c \
           platform/dummy/mpe.c

CFLAGS    += -fdata-sections -ffunction-sections
CFLAGS    += -I$(AM_HOME)/am/src/riscv/ysyxSoC/include
LDSCRIPTS += $(AM_HOME)/scripts/ysyxSoC-linker.ld
#LDFLAGS   += --defsym=_mrom_start=0x20000000 --defsym=_entry_offset=0x0 --defsym=_sram_start=0x0f000000
LDFLAGS   += --gc-sections -e _start

MAINARGS_MAX_LEN = 64
MAINARGS_PLACEHOLDER = the_insert-arg_rule_in_Makefile_will_insert_mainargs_here
CFLAGS += -DMAINARGS_MAX_LEN=$(MAINARGS_MAX_LEN) -DMAINARGS_PLACEHOLDER=$(MAINARGS_PLACEHOLDER)

insert-arg: image
	@python $(AM_HOME)/tools/insert-arg.py $(IMAGE).bin $(MAINARGS_MAX_LEN) $(MAINARGS_PLACEHOLDER) "$(mainargs)"

image: image-dep
	@$(OBJDUMP) -d $(IMAGE).elf > $(IMAGE).txt
	@echo + OBJCOPY "->" $(IMAGE_REL).bin
	@$(OBJCOPY) -S --set-section-flags .bss=alloc,contents -O binary $(IMAGE).elf $(IMAGE).bin

run: insert-arg
	$(MAKE) -C $(NPC_HOME) sim IMG=$(IMAGE).bin ELF=$(IMAGE).elf

# 用 NEMU 运行镜像，生成 icachesim 的输入文件（每行一个十六进制 PC）。
# 要求 NEMU 配置启用：TARGET_NATIVE_ELF / RESET_FLASH / YSYXSOC_DEVICE
NEMU_BINARY ?= $(NEMU_HOME)/build/riscv32-nemu-interpreter
TRACE_DIR ?= $(dir $(IMAGE))

icachesim-itrace: insert-arg
	@mkdir -p $(TRACE_DIR)
	@echo + NEMU "->" $(TRACE_DIR)icachesim-itrace.txt
	@$(NEMU_BINARY) -b -l $(TRACE_DIR)$(notdir $(IMAGE)).log -i $(TRACE_DIR)icachesim-itrace.txt $(IMAGE).bin
	@echo "icachesim-itrace generated: $(TRACE_DIR)icachesim-itrace.txt"

.PHONY: insert-arg icachesim-itrace
