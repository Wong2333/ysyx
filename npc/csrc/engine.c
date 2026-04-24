#include <time.h>
#include <nvboard.h>
#include <VTop.h>
// #define DEBUG
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
extern "C" void printCurrentInst();
void cpu_exec(int n){
    STATE = RUNNING;
    if(n > 0){
        for(int i = 0;i<n;i++){
            single_cycle();
            if(STATE == STOP) break;
        }
        printCurrentInst();
    }else{
        while(1){
            single_cycle();
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
    STATE = STOP;
}

void sdb_mainloop();

void start_engine(){
    while(1) {
        sdb_mainloop();
    }
}