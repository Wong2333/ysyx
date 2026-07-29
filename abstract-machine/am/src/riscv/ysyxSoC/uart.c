#include <am.h>
#include <npc.h>
#include <riscv/riscv.h>

void __am_uart_rx(AM_UART_RX_T *uart_rx){
    uart_rx->data = inb(UART_BASE + UART_REG_XR);
}