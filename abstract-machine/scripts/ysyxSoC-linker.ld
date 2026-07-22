ENTRY(_start)

MEMORY {
  flash : ORIGIN = 0x30000000, LENGTH = 16M
  psram : ORIGIN = 0x80000000, LENGTH = 4M
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
    _bootloader_start = .;

    *(.text*)
  } > psram AT> flash
  _bootloader_lma_start = LOADADDR(.text);
  .rodata : {
    *(.rodata*)
  } > psram AT> flash

  .data : {
    *(.data*)
    *(.sdata*)
  } > psram AT> flash
  _bootloader_end = .;

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
