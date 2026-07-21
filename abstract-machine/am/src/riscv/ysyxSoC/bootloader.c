#include <riscv/riscv.h>

extern uint8_t _SSBL_lma_start;
extern uint8_t _SSBL_start;
extern uint8_t _SSBL_end;

void first_stage_bootloader() __attribute__((section(".FSBL")));

void first_stage_bootloader(){
  uint8_t *dst = &_SSBL_start;
  uint8_t *src = &_SSBL_lma_start;
  uint8_t *end = &_SSBL_end;
  // for(int start = _bootloader_start )
  while(dst < end){
    *dst++ = *src++; 
  }
  //memcpy(&_bootloader_start,&_bootloader_lma_start,data_size);
}

extern uint8_t _bootloader_lma_start;
extern uint8_t _bootloader_start;
extern uint8_t _bootloader_end;

void second_stage_bootloader() __attribute__((section(".SSBL")));


void second_stage_bootloader(){
  uint8_t *dst = &_bootloader_start;
  uint8_t *src = &_bootloader_lma_start;
  uint8_t *end = &_bootloader_end;
  uint8_t* end_ = (uint8_t*)((uintptr_t)end & ~(0b11));
  // for(int start = _bootloader_start )
  while(dst < end_) *dst++ = *src++; 
  while(dst < end) *dst++ = *src++; 
}