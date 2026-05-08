#include <stdlib.h>
#include <time.h>
#include <nvboard.h>
#include <VTop.h>
// #define DEBUG
#include <difftest.h>
#include <autoconfig.h>
static TOP_NAME top;
struct timespec start, end;
bool check_wp();
void nvboard_bind_all_pins(TOP_NAME* top);
void load_img(const char* path);

typedef enum {
  RUNNING,
  STOP
}ENGINE_STATE_TYPE;

ENGINE_STATE_TYPE STATE = STOP;

static void single_cycle() {
  top.clock = ~top.clock; 
  top.eval();
  top.clock = ~top.clock; 
  top.eval();
  nvboard_update();
  if(check_wp()){
    STATE = STOP;
  }
}
extern uint32_t *mem;
extern uint32_t img_size;
extern "C" void printCurrentInst();
uint32_t read_pc_reg();
void cpu_exec(int n){
    printf("[CPU_EXEC] entering with n = %d\n", n);
    fflush(stdout);
    uint64_t inst_cnt = 0;
    STATE = RUNNING;
    if(n > 0){
        for(int i = 0;i<n;i++){
            uint32_t current_pc = read_pc_reg();
            single_cycle();
            if(STATE == STOP) break;
            check_difftest(current_pc, read_pc_reg());
            inst_cnt++;
            if (inst_cnt % 10000 == 0) {
                printf("[CPU_EXEC] executed %lu instructions, pc=0x%08x\n", inst_cnt, read_pc_reg());
                fflush(stdout);
            }
        }
        printCurrentInst();
    }else{
        while(1){
            uint32_t current_pc = read_pc_reg();
            single_cycle();
            if(STATE == STOP) break;
            check_difftest(current_pc, read_pc_reg());
            inst_cnt++;
            if (inst_cnt % 10000 == 0) {
                printf("[CPU_EXEC] executed %lu instructions, pc=0x%08x\n", inst_cnt, read_pc_reg());
                fflush(stdout);
            }
        }
    }
    printf("[CPU_EXEC] exiting, executed %lu instructions\n", inst_cnt);
    fflush(stdout);
}
void init_sdb();

void init_mem(const char* img_path){
    if(img_path != NULL)
        load_img(img_path);
}

void init_cpu(){
    // 1. 开始计时 (获取当前进程的 CPU 时钟)
    clock_gettime(CLOCK_PROCESS_CPUTIME_ID, &start);
    top.clock = 0;
    top.reset = 1;
    top.eval();
    top.clock = 1;
    top.eval();
    top.reset = 0;
    top.clock = 0;
    top.eval();
}

void init_nvboard(){
    nvboard_bind_all_pins(&top);
    nvboard_init(); 
}

void init_engine(int argc,char**argv){
    init_nvboard();
#ifdef CONFIG_ENABLE_SDB
    init_sdb();
#endif
    if(argc >= 2)
        init_mem(argv[1]);
    else init_mem(NULL);
    init_cpu();
    printf("[ENGINE] CONFIG_START_ADDR = 0x%x, img_size = %u\n", CONFIG_START_ADDR, img_size);
    fflush(stdout);
#ifdef CONFIG_ENABLE_DIFFTEST
    printf("[ENGINE] CONFIG_ENABLE_DIFFTEST is defined, argc = %d\n", argc);
    fflush(stdout);
    {
        const char *ref_so = NULL;
        if (argc >= 3) {
            ref_so = argv[2];
            printf("[ENGINE] Using argv[2] as ref SO: %s\n", ref_so);
        }
        if (ref_so == NULL) {
            ref_so = getenv("NEMU_REF_SO");
            if (ref_so) printf("[ENGINE] Using NEMU_REF_SO: %s\n", ref_so);
        }
        if (ref_so == NULL) {
            ref_so = "../nemu/build/riscv32-nemu-interpreter-so";
            printf("[ENGINE] Using default ref SO: %s\n", ref_so);
        }
        fflush(stdout);
        init_difftest(ref_so, img_size);
    }
#else
    printf("[ENGINE] CONFIG_ENABLE_DIFFTEST is NOT defined\n");
    fflush(stdout);
#endif
    printf("[ENGINE] init_difftest returned, img_size = %u\n", img_size);
    fflush(stdout);
    STATE = STOP;
}

void sdb_mainloop();

void start_engine(){
    while(1) {
        sdb_mainloop();
    }
}