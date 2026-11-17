.section entry, "ax"
.globl _start
.type _start, @function

_start:
  mv s0, zero
  la sp, _heap_start
  call first_stage_bootloader
  call second_stage_bootloader
  call _trm_init

.size _start, . - _start
