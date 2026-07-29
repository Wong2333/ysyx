#include <am.h>
#include <npc.h>
#include <riscv/riscv.h>

void __am_uart_rx(AM_UART_RX_T *uart_rx){
    uint8_t data = inb(UART_BASE + UART_REG_XR);
    if(data == 0){
        uart_rx->data = 0xff;
    }
    else uart_rx->data = data;
}