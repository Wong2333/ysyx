#include <common.h>

FILE *mtrace_fp = NULL;
uint64_t nr_mem_trace = 0;

void init_mtrace(){
#ifdef CONFIG_MTRACE
    FILE *fp = fopen("./mtrace.txt", "w");
    Assert(fp, "Can not open '%s'", "./mtrace.txt");
    mtrace_fp = fp;
    Log("mtrace is written to ./mtrace.txt");
#endif
}

extern uint64_t nr_mem_trace;
bool mtrace_enable() {
  return MUXDEF(CONFIG_MTRACE, (nr_mem_trace >= CONFIG_MTRACE_START) &&
      (nr_mem_trace <= CONFIG_MTRACE_END), false);
}

