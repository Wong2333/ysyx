ENTRY(_start)

MEMORY {
  mrom : ORIGIN = 0x20000000, LENGTH = 4K
  sram : ORIGIN = 0x0f000000, LENGTH = 8K
}

SECTIONS {
  /* _pmem_start and _entry_offset are defined in LDFLAGS */
  . = ORIGIN(mrom);
  .text : {
    *(entry)
    *(.text*)
  } > mrom AT> mrom

  .rodata : {
    *(.rodata*)
  } > mrom AT> mrom

  . = ORIGIN(sram);
  .data : {
    _data_vma_start = .;
    *(.data*)
    *(.sdata*)
    _data_vma_end = .;
    _data_lma_start = LOADADDR(.data);
  } > sram AT> mrom

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
