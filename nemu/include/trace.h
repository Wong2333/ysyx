#ifndef _TRACE_H_
#define _TRACE_H_
#include <common.h>

typedef enum {
  WRITE_MTRACE,
  READ_MTRACE
} MTRACE_MODE;
void record_mtrace(MTRACE_MODE mode,paddr_t addr,int len,word_t data);
void record_ftrace(uint32_t pc, uint32_t target_pc, int rd, int rs1);

#endif