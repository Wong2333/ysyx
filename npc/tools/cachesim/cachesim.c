// icachesim: 模拟 npc 的 icache，统计取指缺失次数
//
// 输入：NEMU -i 生成的 icachesim-itrace.bin（顺序段记录，
//       每段 8 字节小端 {uint32 start_pc, uint32 end_pc}，展开即逐指令 PC）
// 模拟：与 npc/src/icache/icache.scala 一致（默认 block_size=16, block_num=16,
//       直接映射：index = addr[7:4]，tag = addr[31:8]，valid+tag 比较）
//
// 用法: cachesim [-b block_size] [-n block_num] <file>
//       参数可覆盖（用于 cache 设计空间探索）

#include <stdio.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

static int log2_ceil(int v) {
  int n = 0;
  while ((1 << n) < v) n++;
  return n;
}

int main(int argc, char *argv[]) {
  int block_size = 16;
  int block_num = 16;
  const char *file = NULL;

  for (int i = 1; i < argc; i++) {
    if (strcmp(argv[i], "-b") == 0 && i + 1 < argc) {
      block_size = atoi(argv[++i]);
    } else if (strcmp(argv[i], "-n") == 0 && i + 1 < argc) {
      block_num = atoi(argv[++i]);
    } else {
      file = argv[i];
    }
  }
  if (file == NULL || block_size < 4 || block_num < 1 ||
      (block_size & (block_size - 1)) || (block_num & (block_num - 1))) {
    fprintf(stderr, "usage: %s [-b block_size] [-n block_num] <icachesim-itrace.bin>\n", argv[0]);
    return 1;
  }

  int offset_bits = log2_ceil(block_size);
  int index_bits = log2_ceil(block_num);
  int index_mask = block_num - 1;

  uint8_t *valid = calloc(block_num, sizeof(uint8_t));
  uint32_t *tag_arr = malloc(block_num * sizeof(uint32_t));

  FILE *fp = fopen(file, "rb");
  if (fp == NULL) {
    fprintf(stderr, "can not open %s\n", file);
    return 1;
  }

  uint64_t total = 0, miss = 0;
  uint32_t start, end;
  while (fread(&start, 4, 1, fp) == 1 && fread(&end, 4, 1, fp) == 1) {
    for (uint32_t pc = start; pc <= end; pc += 4) {
      uint32_t idx = (pc >> offset_bits) & index_mask;
      uint32_t tag = pc >> (offset_bits + index_bits);
      if (!valid[idx] || tag_arr[idx] != tag) {
        miss++;
        valid[idx] = 1;
        tag_arr[idx] = tag;
      }
      total++;
    }
  }
  fclose(fp);

  printf("block_size = %d, block_num = %d\n", block_size, block_num);
  printf("accesses = %lu, misses = %lu, miss rate = %.4f%%\n",
      total, miss, total ? 100.0 * miss / total : 0.0);

  free(valid);
  free(tag_arr);
  return 0;
}
