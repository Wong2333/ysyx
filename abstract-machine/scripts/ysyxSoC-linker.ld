ENTRY(_start)

MEMORY {
  flash : ORIGIN = 0x30000000, LENGTH = 16M
  psram : ORIGIN = 0x80000000, LENGTH = 4M
  sdram : ORIGIN = 0xa0000000, LENGTH = 32M
}

SECTIONS {
  /* _pmem_start and _entry_offset are defined in LDFLAGS */
  . = ORIGIN(flash);
  .text_start : {
    *(entry)
    *(.FSBL)
  } > flash AT> flash

  . = ORIGIN(psram);
  _pmem_start = .;

  .SSBL : {
    _SSBL_start = .;
    *(.SSBL)
    _SSBL_end = .;
    _SSBL_lma_start = LOADADDR(.SSBL);
  } > psram AT> flash


  .text : {
    _text_vma_start = .;
    *(.text*)
    *(.rodata*)
    _text_vma_end = .;
  } > psram AT> flash
  _text_lma_start = LOADADDR(.text);

  .data : {
    _data_vma_start = .;
    *(.data*)
    *(.sdata*)
    _data_vma_end = .;
  } > psram AT> flash
  _data_lma_start = LOADADDR(.data);

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
