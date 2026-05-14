#include <stdlib.h>
#include <time.h>
#include <nvboard.h>
#include <VTop.h>
#include <autoconfig.h>
// #define DEBUG
#include <difftest.h>
#include <trace.h>
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
    STATE = RUNNING;
    if(n > 0){
        for(int i = 0;i<n;i++){
            uint32_t current_pc = read_pc_reg();
            single_cycle();
            uint32_t next_pc = read_pc_reg();
            trace_and_difftest(current_pc, mem[current_pc >> 2], next_pc);
            check_difftest(current_pc, next_pc);
            if(STATE == STOP) break;
        }
        printCurrentInst();
    }else{
        while(1){
            uint32_t current_pc = read_pc_reg();
            single_cycle();
            uint32_t next_pc = read_pc_reg();
            trace_and_difftest(current_pc, mem[current_pc >> 2], next_pc);
            check_difftest(current_pc, next_pc);
            if(STATE == STOP) break;
        }
    }
}
void init_sdb();

void init_mem(const char* img_path){
    if(img_path != NULL)
        load_img(img_path);
}

void init_cpu(){
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
    const char *elf_file = (argc >= 2) ? argv[1] : NULL;
    init_trace(elf_file);
#ifdef CONFIG_ENABLE_DIFFTEST
    {
        const char *ref_so = NULL;
        if (argc >= 3) {
            ref_so = argv[2];
        }
        if (ref_so == NULL) {
            ref_so = getenv("NEMU_REF_SO");
        }
        if (ref_so == NULL) {
            ref_so = "../nemu/build/riscv32-nemu-interpreter-so";
        }
        init_difftest(ref_so, img_size);
    }
#endif
    STATE = STOP;
}

void sdb_mainloop();

void start_engine(){
    while(1) {
        sdb_mainloop();
    }
}
