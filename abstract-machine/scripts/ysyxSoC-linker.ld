ENTRY(_start)

MEMORY {
  flash : ORIGIN = 0x30000000, LENGTH = 16M
  psram : ORIGIN = 0x80000000, LENGTH = 4M
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

  . = ORIGIN(psram);
  .data : {
    _data_vma_start = .;
    *(.data*)
    *(.sdata*)
    _data_vma_end = .;
    _data_lma_start = LOADADDR(.data);
  } > psram AT> flash

  .bss : {
	_bss_start = .;
    *(.bss*)
    *(.sbss*)
    *(.scommon)
  } > psram

  . = ALIGN(8);
  _heap_start = .;

  _stack_pointer = ORIGIN(psram) + LENGTH(psram);

}
