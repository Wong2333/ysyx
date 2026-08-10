#include <riscv/riscv.h>
#include <am.h>

extern uint32_t _SSBL_lma_start;
extern uint32_t _SSBL_start;
extern uint32_t _SSBL_end;

void first_stage_bootloader() __attribute__((section(".FSBL")));

void first_stage_bootloader(){
  uint32_t *dst = &_SSBL_start;
  uint32_t *src = &_SSBL_lma_start;
  uint32_t *end = &_SSBL_end;
  // for(int start = _bootloader_start )
  while(dst < end){
    *dst = *src;
    dst++;
    src++; 
  }
  halt(1);
  //volatile int32_t wait = 10000;
  //while(wait-- > 0);
  //memcpy(&_bootloader_start,&_bootloader_lma_start,data_size);
}

extern uint32_t _text_lma_start;
extern uint32_t _text_vma_start;
extern uint32_t _text_vma_end;
extern uint32_t _data_lma_start;
extern uint32_t _data_vma_start;
extern uint32_t _data_vma_end;

void second_stage_bootloader() __attribute__((section(".SSBL")));


void second_stage_bootloader(){
  uint32_t *dst = &_text_vma_start;
  uint32_t *src = &_text_lma_start;
  uint32_t *end = &_text_vma_end;
  // for(int start = _bootloader_start )
  while(dst < end){
    *dst = *src;
    dst++;
    src++;
  } 

  dst = &_data_vma_start;
  src = &_data_lma_start;
  end = &_data_vma_end;
  // for(int start = _bootloader_start )
  while(dst < end){
    *dst = *src;
    dst++;
    src++;
  } 

}