#include <svdpi.h>
#include <VysyxSoCFull__Dpi.h>
#include <stdint.h> // 引入 uint32_t, uint8_t 等标准类型
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <mmio.h>
#include <autoconfig.h>
#include <trace.h>
#include <cassert>
// 假设内存大小为 4MB (1M * 4Bytes)

extern struct timespec start, end;

void difftest_skip_ref();

static inline bool in_pmem(uint32_t addr) {
  return addr - CONFIG_START_ADDR < 0x80000000;
}

uint32_t img[1024*1024] = {
  0x00c00593, // addi a1, zero, 12
  0x00b58633, // add a2, a1, a1
  0xff160693, // addi a3, a2, -15
  0x00f68767, // jalr a4, 15(a3)
  0x02000067, // jalr zero, 32(zero)
  0x01000067,
  0x00070067, // jalr zero, 0(a4)
  0x01000067,
  0x000027b7, // 32: lui a5,2
  0x0245a803, // lw a6, 36(a1)
  0x030585a3, // sb a6 43(a1)
  0x00100513, // addi a0 zero,1
  0x00100073, // ebreak
  0x44332211,
  0x00000000,
  0x00000000
};

uint32_t mrom[1024];
uint32_t mrom_size = 4*1024;

#define FLASH_SIZE  (16*1024*1024)
uint32_t flash[FLASH_SIZE / 4];

uint32_t img_size = 0;

void load_img(const char* path){

    uint8_t *pmem_byte = (uint8_t *)flash;
    FILE* fp = fopen(path,"r");
    if(fp == NULL){
        printf("can't open this file: %s\n",path);
        exit(1);
    }
    size_t len;
    uint32_t idx = 0;
    printf("loading images....\n");
    while((len = fread(pmem_byte+idx,1,1024,fp)) > 0){
        idx += len;
    }
    printf("loaded\n");
    img_size = idx;
    fclose(fp);
}

extern "C" void flash_read(int32_t addr, int32_t *data) {
    uint32_t uaddr = addr;
    *data = (int32_t)flash[uaddr >> 2];
}



extern "C" void mrom_read(int32_t addr, int32_t *data) {
    uint32_t uaddr = addr;
    uaddr = uaddr - 0x20000000;
    *data = (int32_t)mrom[uaddr >> 2];
    // data = mrom[addr >> 2];
}

uint32_t psram[1024*1024];

extern "C" void psram_read(int32_t addr,int32_t *data){
    uint32_t uaddr = addr;
    *data = (int32_t)psram[uaddr >> 2];
    //printf("read addr: %x data: %x\n",uaddr,*data);
}

extern "C" void psram_write(int32_t addr,char data){
    uint32_t uaddr = addr;
    uint8_t *psram_8bits = (uint8_t*)psram;
    psram_8bits[uaddr] = data;
}

uint16_t sdram_l [32*1024*1024/2];
uint16_t sdram_h [32*1024*1024/2];
extern "C" void sdram_read(char high, int32_t addr, int16_t* data){
    //printf("dsram:%x\n",addr);
    if(high) *data = sdram_h[addr];
    else *data = sdram_l[addr];
    //printf("data: %x\n",*data);
}

extern "C" void sdram_write(char high, int32_t addr, int16_t wdata, char mask) {
    uint16_t current_data = high? sdram_h[addr] : sdram_l[addr];
    uint16_t new_data = current_data;
    //printf("write: addr: %x, data: %x, mask: %x\n",addr,wdata,mask);

    uint8_t low_enable = !(mask & 0x1);
    uint8_t high_enable = !(mask & 0x2);
    if (low_enable) {
        new_data = (new_data & 0xFF00) | (wdata & 0x00FF);
    }

    if (high_enable) {
        new_data = (new_data & 0x00FF) | (wdata & 0xFF00);
    }
    if(high) sdram_h[addr] = new_data;
    else sdram_l[addr] = new_data;
}


/* 总的内存访问函数，供 SDB 调用。
 * 根据地址范围路由到对应的存储数组，和 CPU 通过 APB 外设看到的数据一致。
 */
uint32_t mem_read(uint32_t addr) {
    if (addr >= 0x20000000 && addr < 0x20001000)         // MROM
        return mrom[(addr - 0x20000000) >> 2];
    else if (addr >= 0x30000000 && addr < 0x40000000)    // Flash XIP
        return flash[(addr - 0x30000000) >> 2];
    else if (addr >= 0x80000000 && addr < 0x80400000)    // PSRAM
        return psram[(addr - 0x80000000) >> 2];
    else if(addr >= 0xa0000000 && addr < 0xa2000000){
        return ((uint32_t)sdram_h[addr - 0xa0000000] << 16 | sdram_l[addr - 0xa0000000]);
    }
    return 0;
}

bool in_mem(uint32_t addr){
    if (addr >= 0x20000000 && addr < 0x20001000)         // MROM
        return true;
    else if (addr >= 0x30000000 && addr < 0x40000000)    // Flash XIP
        return true;
    else if (addr >= 0x80000000 && addr < 0x80400000)    // PSRAM
        return true;
    else if(addr >= 0xa0000000 && addr < 0xa2000000)     // SDRAM
        return true;
    return false;
}

