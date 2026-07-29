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


void uart_init() {
  // 1. 设置 LCR 的第 7 位 (DLAB) 为 1，开启除数寄存器访问
  outb(UART_BASE + UART_REG_LC, 0x80);

  // 2. 设置除数 (波特率)
  // 在仿真环境中，简单设置为 1 即可 (DLL=1, DLM=0)
  outb(UART_BASE + UART_REG_DL1, 0x1); // 除数低字节
  outb(UART_BASE + UART_REG_DL2, 0x00); // 除数高字节

  // 3. 设置通信格式 (8N1: 8位数据, 无校验, 1停止位) 并关闭 DLAB
  // 0x03 = 0000_0011b
  // bit 7 (DLAB) = 0 (访问正常寄存器)
  // bit 1-0 = 11 (8位数据长度)
  outb(UART_BASE + UART_REG_LC, 0x03);
}
extern uint32_t _data_lma_start;
extern uint32_t _data_vma_start;
extern uint32_t _data_vma_end;

void show_id_in_seg(uint32_t marchid){
  uint32_t seg = 0;
  while(marchid > 0){
    seg = (seg >> 4) | (marchid % 10) << 28;
    marchid /= 10;
  }
  outl(GPIO_SEG, seg);
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
  show_id_in_seg(marchid);
  int ret = main(mainargs);
  halt(ret);
}

