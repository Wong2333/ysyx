#include <am.h>
#include <klib-macros.h>
#include <klib.h>
#include <npc.h>
#include <riscv/riscv.h>
extern char _heap_start;
int main(const char *args);

extern char _pmem_start;
#define PMEM_SIZE (4 * 1024 * 1024)
#define PMEM_END  ((uintptr_t)&_pmem_start + PMEM_SIZE)

Area heap = RANGE(&_heap_start, PMEM_END);
static const char mainargs[MAINARGS_MAX_LEN] = TOSTRING(MAINARGS_PLACEHOLDER); // defined in CFLAGS

void putch(char ch) {
  while ((inb(UART_BASE + UART_REG_LS) & UART_LS_THRE) == 0);

  outb(UART_BASE,ch);
}

void halt(int code) {
  asm volatile("ebreak");
  while (1);
}

extern uint8_t _bootloader_lma_start;
extern uint8_t _bootloader_start;
extern uint8_t _bootloader_end;

void bootloader() __attribute__((section(".bootloader")));

void bootloader(){
  uint8_t *dst = &_bootloader_start;
  uint8_t *src = &_bootloader_lma_start;
  uint8_t *end = &_bootloader_end;
  // for(int start = _bootloader_start )
  while(dst < end){
    *dst++ = *src++; 
  }
  //memcpy(&_bootloader_start,&_bootloader_lma_start,data_size);
}


void uart_init() {
  // 1. 设置 LCR 的第 7 位 (DLAB) 为 1，开启除数寄存器访问
  outb(UART_BASE + UART_REG_LC, 0x80);

  // 2. 设置除数 (波特率)
  // 在仿真环境中，简单设置为 1 即可 (DLL=1, DLM=0)
  outb(UART_BASE + UART_REG_DL1, 0x10); // 除数低字节
  outb(UART_BASE + UART_REG_DL2, 0x00); // 除数高字节

  // 3. 设置通信格式 (8N1: 8位数据, 无校验, 1停止位) 并关闭 DLAB
  // 0x03 = 0000_0011b
  // bit 7 (DLAB) = 0 (访问正常寄存器)
  // bit 1-0 = 11 (8位数据长度)
  outb(UART_BASE + UART_REG_LC, 0x03);
}

void _trm_init() {
  uart_init();
  char mvendorid[5];
  mvendorid[4] = 0;
  uint32_t marchid;
  asm volatile("csrr %0, mvendorid":"=r"(*(uint32_t *)mvendorid)::);
  asm volatile("csrr %0, marchid":"=r"(marchid)::);
  putch(mvendorid[3]); putch(mvendorid[2]); putch(mvendorid[1]); putch(mvendorid[0]);
  putch('_');
  printf("%u\n",marchid);
  bootloader();
  int ret = main(mainargs);
  halt(ret);
}

