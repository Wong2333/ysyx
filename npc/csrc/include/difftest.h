#ifndef __NPC_DIFFTEST_H__
#define __NPC_DIFFTEST_H__

#include <stdint.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

void init_difftest(const char *ref_so_file, long img_size);
void check_difftest(uint32_t pc);
bool difftest_enabled();
void trace_and_difftest(uint32_t current_pc, uint32_t next_pc);
uint32_t get_commit_pc(void);
uint32_t get_commit_inst(void);
int get_commit_count(void);

#ifdef __cplusplus
}
#endif

#endif
