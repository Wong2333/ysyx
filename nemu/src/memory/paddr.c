/***************************************************************************************
* Copyright (c) 2014-2024 Zihao Yu, Nanjing University
*
* NEMU is licensed under Mulan PSL v2.
* You can use this software according to the terms and conditions of the Mulan PSL v2.
* You may obtain a copy of Mulan PSL v2 at:
*          http://license.coscl.org.cn/MulanPSL2
*
* THIS SOFTWARE IS PROVIDED ON AN "AS IS" BASIS, WITHOUT WARRANTIES OF ANY KIND,
* EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO NON-INFRINGEMENT,
* MERCHANTABILITY OR FIT FOR A PARTICULAR PURPOSE.
*
* See the Mulan PSL v2 for more details.
***************************************************************************************/

#include <memory/host.h>
#include <memory/paddr.h>
#include <device/mmio.h>
#include <isa.h>
#include <utils.h>

#if   defined(CONFIG_PMEM_MALLOC)
static uint8_t *pmem = NULL;
#else // CONFIG_PMEM_GARRAY
static uint8_t pmem[CONFIG_MSIZE] PG_ALIGN = {};
#endif

static uint8_t mrom[CONFIG_MROM_SIZE] = {};
static uint8_t sram[CONFIG_SRAM_SIZE] = {};
static uint8_t flash[CONFIG_FLASH_SIZE] = {};
static uint8_t sdram[CONFIG_SDRAM_SIZE] = {};

uint8_t* guest_to_host(paddr_t paddr) {
  if(in_pmem(paddr))
    return pmem + paddr - CONFIG_MBASE; 
  else if(in_mrom(paddr)){
    return mrom + paddr - CONFIG_MROM_BASE;
  }
  else if(in_sram(paddr)){
    return sram + paddr - CONFIG_SRAM_BASE;
  }
  else if(in_flash(paddr)){
    return flash + paddr - CONFIG_FLASH_BASE;
  }
  else if(in_sdram(paddr)){
    return sdram + paddr - CONFIG_SDRAM_BASE;
  }
  return 0;
}
paddr_t host_to_guest(uint8_t *haddr) { return haddr - pmem + CONFIG_MBASE; }

uint8_t* mrom_guest_to_host(paddr_t paddr) { return mrom + paddr - CONFIG_MROM_BASE; }
uint8_t* sram_guest_to_host(paddr_t paddr) { return sram + paddr - CONFIG_SRAM_BASE; }

static word_t pmem_read(paddr_t addr, int len) {
  word_t ret = host_read(guest_to_host(addr), len);
  return ret;
}

static void pmem_write(paddr_t addr, int len, word_t data) {
  host_write(guest_to_host(addr), len, data);
}

void print_itrace();
static void out_of_bound(paddr_t addr) {
  print_itrace();
  panic("address = " FMT_PADDR " is out of bound of pmem at pc = " FMT_WORD,
      addr, cpu.pc);
}

void init_mem() {
#if   defined(CONFIG_PMEM_MALLOC)
  pmem = malloc(CONFIG_MSIZE);
  assert(pmem);
#endif
  IFDEF(CONFIG_MEM_RANDOM, memset(pmem, rand(), CONFIG_MSIZE));
  Log("physical memory area [" FMT_PADDR ", " FMT_PADDR "]", PMEM_LEFT, PMEM_RIGHT);
}


#ifdef CONFIG_YSYXSOC_DEVICE
// ysyxSoC 串口（16550 @ 0x10000000）：写数据寄存器输出字符，
// 读线路状态寄存器（0x5）恒返回 THRE 置位（发送保持寄存器空）
static inline bool in_uart(paddr_t addr) {
  return addr >= 0x10000000 && addr < 0x10001000;
}
// ysyxSoC 其他外设：GPIO(0x10002000)/键盘(0x10011000)/VGA(0x21000000)，
// NEMU 不模拟其行为，读返回 0、写忽略
static inline bool in_ysyxsoc_dev(paddr_t addr) {
  return (addr >= 0x10002000 && addr < 0x10003000) ||
         (addr >= 0x10011000 && addr < 0x10012000) ||
         (addr >= 0x21000000 && addr < 0x21200000);
}
#endif

word_t paddr_read(paddr_t addr, int len) {
#ifdef CONFIG_YSYXSOC_DEVICE
  if (in_uart(addr)) {
    return 0x20; // UART_LS_THRE
  }
  if (in_ysyxsoc_dev(addr)) {
    return 0;
  }
#endif
  if (likely(in_pmem(addr) || in_mrom(addr) || in_sram(addr)
    || in_flash(addr) || in_sdram(addr))){
    record_mtrace(WRITE_TRACE,addr,len, pmem_read(addr, len));
    return pmem_read(addr, len);
  }
  IFDEF(CONFIG_DEVICE, return mmio_read(addr, len));
  out_of_bound(addr);
  return 0;
}

void paddr_write(paddr_t addr, int len, word_t data) {
#ifdef CONFIG_YSYXSOC_DEVICE
  if (in_uart(addr)) {
    if (addr == 0x10000000) {
      putchar(data & 0xff);
      fflush(stdout);
    }
    return;
  }
  if (in_ysyxsoc_dev(addr)) {
    return;
  }
#endif
  if (likely(in_pmem(addr) || in_sram(addr)|| in_flash(addr) || in_sdram(addr))) {
    record_mtrace(READ_TRACE,addr,len, data);
    pmem_write(addr, len, data);
    return;
  }
  IFDEF(CONFIG_DEVICE, mmio_write(addr, len, data); return);

  out_of_bound(addr);
}
