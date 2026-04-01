#include <common.h>
#include <trace.h>
FILE *mtrace_fp = NULL;

void init_mtrace(){
#ifdef CONFIG_MTRACE
    FILE *fp = fopen("./mtrace.txt", "w");
    Assert(fp, "Can not open '%s'", "./mtrace.txt");
    mtrace_fp = fp;
    Log("mtrace is written to ./mtrace.txt");
#endif
}


bool mtrace_enable(paddr_t addr) {
  return MUXDEF(CONFIG_MTRACE, (addr >= CONFIG_MTRACE_START_ADDR) &&
      (addr <= CONFIG_MTRACE_END_ADDR), false);
}

void record_mtrace(MTRACE_MODE mode,paddr_t addr,int len,word_t data){
#ifdef CONFIG_MTRACE
    if (mtrace_enable(addr) && mtrace_fp != NULL) { 
        if(mode == READ_MTRACE)
            fprintf(mtrace_fp,"read memory: raddr=%u rlen=%u rdata=%u \n",addr,len, data); 
        else
            fprintf(mtrace_fp,"write memory: waddr=%u wlen=%u wdata=%u \n",addr,len, data); 
        fflush(mtrace_fp); 
    } 

#endif
}
