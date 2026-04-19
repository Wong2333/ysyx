#include <time.h>
#include <nvboard.h>
#include <VTop.h>
// #define DEBUG
static TOP_NAME top;
struct timespec start, end;

void nvboard_bind_all_pins(TOP_NAME* top);
void load_img(const char* path);
extern "C" void init_sdb();

static void single_cycle() {
  top.clock = ~top.clock; 
  top.eval();
  top.clock = ~top.clock; 
  top.eval();
  nvboard_update();
}

void cpu_exec(int n){
  if(n > 0){
    for(int i = 0;i<n;i++){
      single_cycle();
    }
  }else{
    while(1){
      single_cycle();
    }
  }
}

void reset(const char *path){
  init_sdb();
  if(path != NULL)
    load_img(path);
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
void sdb_mainloop();
int main(int argc,char**argv) {
  nvboard_bind_all_pins(&top);
  nvboard_init(); 
  if(argc >= 2)
    reset(argv[1]);
  else reset(NULL);
  while(1) {
    sdb_mainloop();
  }
  printf("end!!!\n");
  return 1;
}