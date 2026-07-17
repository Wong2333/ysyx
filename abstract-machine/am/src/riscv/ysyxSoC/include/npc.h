#ifndef NPC_H__
#define NPC_H__

#ifndef UART_BASE
#define UART_BASE 0x10000000 
#endif

#define UART_REG_DL1 0x0 // Divisor Latch Low
#define UART_REG_DL2 0x1 // Divisor Latch High
#define UART_REG_LC  0x3 // Line Control

#define RTC_ADDR         (0x02000000 + 0XBFF8)
#endif