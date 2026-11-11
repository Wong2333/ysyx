#ifdef NPC

#include <stdint.h>
#include <stddef.h>
#include <stdlib.h>
#include <stdio.h>
#include <time.h>

#define NPC_PMEM_BASE 0x80000000u
#define NPC_PMEM_SIZE (128u * 1024u * 1024u)
#define NPC_UART_BASE 0x10000000u
#define NPC_RTC_BASE  (0x10000000u + 0x30u)

extern uint32_t img_size;

static uint8_t npc_pmem[NPC_PMEM_SIZE] __attribute__((aligned(4096)));

static uint64_t npc_now_us() {
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (uint64_t)ts.tv_sec * 1000000ull + (uint64_t)ts.tv_nsec / 1000ull;
}

uint8_t *npc_pmem_base() {
    return npc_pmem;
}

void npc_load_img(const char *path) {
    FILE *fp = fopen(path, "r");
    if (fp == NULL) {
        printf("can't open this file: %s\n", path);
        exit(1);
    }
    size_t len;
    uint32_t idx = 0;
    while ((len = fread(npc_pmem + idx, 1, 1024, fp)) > 0) {
        idx += len;
    }
    img_size = idx;
    fclose(fp);
}

uint32_t npc_mem_read_word(uint32_t addr) {
    addr = addr & ~3u;
    if (addr >= NPC_PMEM_BASE && addr < NPC_PMEM_BASE + NPC_PMEM_SIZE) {
        uint32_t off = addr - NPC_PMEM_BASE;
        uint32_t v = 0;
        for (int i = 0; i < 4; i++) {
            if (off + i < NPC_PMEM_SIZE) {
                v |= (uint32_t)npc_pmem[off + i] << (8 * i);
            }
        }
        return v;
    }
    if (addr >= NPC_RTC_BASE && addr < NPC_RTC_BASE + 8) {
        uint64_t t = npc_now_us();
        return (addr == NPC_RTC_BASE + 4) ? (uint32_t)(t >> 32) : (uint32_t)t;
    }
    return 0;
}

extern "C" void npc_mem_read(int addr, int *data) {
    *data = (int)npc_mem_read_word((uint32_t)addr);
}

extern "C" void npc_mem_write(int addr, int data, char strb) {
    uint32_t a = (uint32_t)addr & ~3u;
    if (a >= NPC_PMEM_BASE && a < NPC_PMEM_BASE + NPC_PMEM_SIZE) {
        uint32_t off = a - NPC_PMEM_BASE;
        for (int i = 0; i < 4; i++) {
            if ((strb & (1 << i)) && off + i < NPC_PMEM_SIZE) {
                npc_pmem[off + i] = (uint8_t)(((uint32_t)data >> (8 * i)) & 0xff);
            }
        }
        return;
    }
    if (a == NPC_UART_BASE) {
        for (int i = 0; i < 4; i++) {
            if (strb & (1 << i)) {
                putchar((uint8_t)(((uint32_t)data >> (8 * i)) & 0xff));
            }
        }
        fflush(stdout);
        return;
    }
}

#endif
