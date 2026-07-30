#include <am.h>
#include <npc.h>
#include <riscv/riscv.h>

void uart_init() {
  // 1. 设置 LCR 的第 7 位 (DLAB) 为 1，开启除数寄存器访问
  outb(UART_BASE + UART_REG_LC, 0x80);

  // 2. 设置除数 (波特率)
  // 在仿真环境中，简单设置为 1 即可 (DLL=1, DLM=0)
  outb(UART_BASE + UART_REG_DL1, 0x2); // 除数低字节
  outb(UART_BASE + UART_REG_DL2, 0x00); // 除数高字节

  // 3. 设置通信格式 (8N1: 8位数据, 无校验, 1停止位) 并关闭 DLAB
  // 0x03 = 0000_0011b
  // bit 7 (DLAB) = 0 (访问正常寄存器)
  // bit 1-0 = 11 (8位数据长度)
  outb(UART_BASE + UART_REG_LC, 0x03);
}

void __am_uart_rx(AM_UART_RX_T *uart_rx){
    if((inb(UART_BASE + UART_REG_LS) & UART_LS_DR) == 0){
        uart_rx->data = 0xff;
    }else uart_rx->data = inb(UART_BASE + UART_REG_XR);
    printf("rx: %x\n",uart_rx->data);
    // uint8_t data = inb(UART_BASE + UART_REG_XR);
    // if(data == 0){
    //     uart_rx->data = 0xff;
    // }
    // else uart_rx->data = data;
}