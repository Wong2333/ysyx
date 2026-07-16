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
uint32_t img_size = 0;
uint32_t *mem = img;
void load_img(const char* path){
    mem = (uint32_t *)malloc(4ULL * 1024 * 1024 * 1024);
    if (!mem) {
        printf("Failed to allocate memory!\n");
        exit(1);
    }
    
    memset(mem, 0, 4ULL * 1024 * 1024 * 1024);
    uint8_t *pmem_byte = (uint8_t *)mem;
    FILE* fp = fopen(path,"r");
    if(fp == NULL){
        printf("can't open this file: %s\n",path);
        exit(1);
    }
    size_t len;
    uint32_t idx = 0;
    while((len = fread(pmem_byte+idx+CONFIG_START_ADDR,1,1024,fp)) > 0){
        idx += len;
    }
    img_size = idx;
    fclose(fp);
}

extern "C" int pmem_read(int addr, int valid, int* error) {
    if(valid == 0) return -1;
    *error = false;
    unsigned long long cpu_time_us;
    // 将有符号地址转为无符号，防止算术移位问题
    uint32_t raddr = (uint32_t)addr;
    // if(addr == RTC_ADDR){
    //     difftest_skip_ref();
    //     clock_gettime(CLOCK_PROCESS_CPUTIME_ID, &end);
    //     // 3. 计算纳秒差值
    //     cpu_time_us = (end.tv_sec - start.tv_sec) * 1000000 
    //             + (end.tv_nsec - start.tv_nsec) / 1000;
    //     return (uint32_t)cpu_time_us; 
    // }else if(addr == RTC_ADDR + 4){
    //     difftest_skip_ref();
    //     clock_gettime(CLOCK_PROCESS_CPUTIME_ID, &end);
    //     // 3. 计算纳秒差值
    //     cpu_time_us = (end.tv_sec - start.tv_sec) * 1000000 
    //             + (end.tv_nsec - start.tv_nsec) / 1000;
    //     return (uint32_t)(cpu_time_us >> 32);
    // }
    if(!in_pmem(raddr)) *error = true;
    uint32_t data = mem[raddr >> 2];

    record_mtrace(READ_TRACE, raddr, 4, data);
    return data;
}

extern "C" int pmem_write(int addr, int data, char mask, int valid) {
    if(valid == 0) return 0;
    uint32_t waddr = (uint32_t)addr;
    uint32_t wdata = (uint32_t)data;
    uint8_t wmask = (uint8_t)mask & 0xF;
    uint8_t *pmem_byte = (uint8_t *)mem;
    waddr = waddr & (~0x3);
    // if(addr == SERIAL_PORT){
    //     difftest_skip_ref();
    //     printf("%c",(char)data);
    //     fflush(stdout);
    //     return 0;
    // }
    if(!in_pmem(waddr)) return true;
    if (wmask & 0x1) {
        pmem_byte[waddr + 0] = (uint8_t)(wdata >> 0);
        // printf("write %x to %x\n",(uint8_t)(wdata >> 0),waddr);
    }
    if (wmask & 0x2) {
        pmem_byte[waddr + 1] = (uint8_t)(wdata >> 8);
        // printf("write %x to %x\n",(uint8_t)(wdata >> 8),waddr+1);
    }
    if (wmask & 0x4) {
        pmem_byte[waddr + 2] = (uint8_t)(wdata >> 16);
        // printf("write %x to %x\n",(uint8_t)(wdata >> 16),waddr+2);
    }
    if (wmask & 0x8) {
        pmem_byte[waddr + 3] = (uint8_t)(wdata >> 24);
        // printf("write %x to %x\n",(uint8_t)(wdata >> 24),waddr+3);
    }
    record_mtrace(WRITE_TRACE, waddr, 4, wdata);
    return false;
}
uint32_t read_pc_reg();
extern "C" void printCurrentInst(){
    printf("pc:0x%8x  inst:0x%8x\n",read_pc_reg(),mem[read_pc_reg() >> 2]);
}


extern "C" void flash_read(int32_t addr, int32_t *data) { assert(0); }


uint32_t mrom[1024] = {
    0x00100073
};
extern "C" void mrom_read(int32_t addr, int32_t *data) {
    printf("%x\n",addr);
    exit(0);
    // data = mrom[addr >> 2];
}
