#ifndef _TRACE_H_
#define _TRACE_H_
#include <common.h>

typedef enum {
  WRITE_TRACE,
  READ_TRACE
} RECORD_MODE;

void init_trace(const char*,const char*);
void record_mtrace(RECORD_MODE mode,paddr_t addr,int len,word_t data);
void record_ftrace(uint32_t pc, uint32_t next_pc, uint32_t target_pc, int rd, int rs1);
void record_dtrace(RECORD_MODE mode,paddr_t addr,int len,word_t data,const char* name);

// icachesim 输入：逐指令 PC 序列（每行一个十六进制 PC），
// 由启动选项 -i/--icachesim=FILE 开启，与 ITRACE 独立
extern FILE *icachesim_fp;
void init_icachesim(const char *file);

#endif