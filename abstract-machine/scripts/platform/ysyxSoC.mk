AM_SRCS := riscv/ysyxSoC/start.S \
           riscv/ysyxSoC/trm.c \
           riscv/ysyxSoC/ioe.c \
           riscv/ysyxSoC/timer.c \
           riscv/ysyxSoC/input.c \
           riscv/ysyxSoC/cte.c \
           riscv/ysyxSoC/trap.S \
           riscv/ysyxSoC/bootloader.c \
           riscv/ysyxSoC/uart.c \
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

.PHONY: insert-arg
