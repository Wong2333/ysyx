#include <nvboard.h>
#include <Vtop.h>
// #define DEBUG
static TOP_NAME top;

void nvboard_bind_all_pins(TOP_NAME* top);

extern uint32_t *mem;
extern void load_img(char* path);

void printMEM(){
  for(int i = 0 ;i<5;i++)
    printf("0x%x\n",mem[i+0x400]);
}

static void single_cycle() {
  top.clk = ~top.clk; 
  // printMEM();
  top.eval();
  // printf("0x%x: 0x%x\n",top.pc,top.inst);
  top.clk = ~top.clk; 
  top.eval();
  // printf("------------------\n");

}
void reset(char*path){
  if(path != NULL)
    load_img(path);
  top.clk = 0;
  top.eval();
  // top.rst = 1;
  // top.eval();
  // top.rst = 1;
  // top.clk = 1;
  // top.eval();
  // top.clk = 0;
  // top.rst = 0;
  // top.eval();
}


int main(int argc,char**argv) {
  nvboard_bind_all_pins(&top);
  nvboard_init();
  if(argc >= 2)
    reset(argv[1]);
  else reset(NULL);
  #ifdef DEBUG
  char cmd[100];
  int cnt = 97;
  while(cnt -- > 0) {
    nvboard_update();
    single_cycle();
  }
  printf("[debug] ");
  while(scanf("%s",cmd) == 1) {
    if(cmd[0] == 's'){
      nvboard_update();
      single_cycle();
    }
    printf("[debug] ");
  }
  #else

  int num = 0;
  while(1) {
    nvboard_update();
    // for(int i = 0;i<10;i++)
    //   printf("mem[%x]=%x\n",0x80000500+i*4,mem[0x80000500/4+i]);
    printf("-----------\n");
    printf("cycle:%d IP:0x%x inst:0x%x\n ",num++,top.pc,top.inst);
    single_cycle();

  }
  #endif


}
