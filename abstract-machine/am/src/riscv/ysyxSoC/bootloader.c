#include <riscv/riscv.h>

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
  //memcpy(&_bootloader_start,&_bootloader_lma_start,data_size);
}

extern uint32_t _text_lma_start;
extern uint32_t _text_vma_start;
extern uint32_t _text_vma_end;
extern uint32_t _data_lma_start;
extern uint32_t _data_vma_start;
extern uint32_t _data_vma_end;
extern uint32_t _rtt_lma_start __attribute__((weak));
extern uint32_t _rtt_vma_start __attribute__((weak));
extern uint32_t _rtt_vma_end __attribute__((weak));

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
  if (&_rtt_vma_start != 0 && &_rtt_vma_end != 0 && &_rtt_vma_start < &_rtt_vma_end) {
    dst = &_rtt_vma_start;
    src = &_rtt_lma_start;
    end = &_rtt_vma_end;
    while(dst < end){
      *dst = *src;
      dst++;
      src++;
    }
  }
}