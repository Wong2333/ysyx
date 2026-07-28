#ifndef __NPC_MEMORY_H__
#define __NPC_MEMORY_H__
#include<stdint.h>
typedef struct MemAccessInfo_{
    int      type;       /* 0 = none, 1 = load, 2 = store */
    uint32_t addr;      /* 实际访问的内存地址 = base + offset */
    uint8_t  size;      /* 读写字节长度: 1 / 2 / 4 */
    int      sign_ext;  /* load 是否需要符号扩展 (store 时无意义) */
    uint32_t data;      /* load: 从内存读回并已扩展的 32 位数据;
                           store: 待写入内存的数据 (已取自 rs2) */
    uint8_t  reg;       /* load 时为 rd, store 时为 rs2 */
    uint8_t  funct3;    /* 原指令 funct3 字段 */
    uint8_t  opcode;    /* 原指令 opcode 字段 */
} MemAccessInfo;

MemAccessInfo get_mem_access_info(uint32_t instr);
#endif