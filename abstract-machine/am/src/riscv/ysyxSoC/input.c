#include <am.h>
#include <npc.h>
#include <riscv/riscv.h>
void __am_input_keybrd(AM_INPUT_KEYBRD_T *kbd) {
  uint8_t code = inb(KEYBOARD);
  if(code == 0xf0){
    while(!(code = inb(KEYBOARD)));
    kbd->keydown = 1;
    kbd->keycode = code;
  }else{
    kbd->keydown = 0;
    kbd->keycode = code;
  }
}
