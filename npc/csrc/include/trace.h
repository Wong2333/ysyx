#ifndef __NPC_TRACE_H__
#define __NPC_TRACE_H__

#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef enum { WRITE_TRACE, READ_TRACE } RECORD_MODE;

void init_trace(const char *trace_dir, const char *elf_file);
void trace_record(uint32_t pc, uint32_t inst, uint32_t dnpc);
void record_mtrace(RECORD_MODE mode, uint32_t addr, int len, uint32_t data);
void record_ftrace(uint32_t pc, uint32_t dnpc, uint32_t inst);
uint32_t get_committed_pc(void);
uint32_t get_committed_inst(void);
int get_commit_cnt(void);

#ifdef __cplusplus
}
#endif

#endif
