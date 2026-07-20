ENTRY(_start)

MEMORY {
  flash : ORIGIN = 0x30000000, LENGTH = 16M
  sram : ORIGIN = 0x0f000000, LENGTH = 8K
}

SECTIONS {
  /* _pmem_start and _entry_offset are defined in LDFLAGS */
  . = ORIGIN(flash);
  .text : {
    *(entry)
    *(.text*)
  } > flash AT> flash

  .rodata : {
    *(.rodata*)
  } > flash AT> flash

  . = ORIGIN(sram);
  .data : {
    _data_vma_start = .;
    *(.data*)
    *(.sdata*)
    _data_vma_end = .;
    _data_lma_start = LOADADDR(.data);
  } > sram AT> flash

  .bss : {
	_bss_start = .;
    *(.bss*)
    *(.sbss*)
    *(.scommon)
  } > sram

  . = ALIGN(8);
  _heap_start = .;

  _stack_pointer = ORIGIN(sram) + LENGTH(sram);

}
