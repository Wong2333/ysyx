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
  printf("0x%x: inst=0x%x\n",top.pc,top.inst);
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
  printf("0x80000020: %x\n",mem[0x80000020/4]);
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
    printf("mem[0x31500]=%x\n",mem[0x31500/4]);
  }
  printf("[debug] ");
  while(scanf("%s",cmd) == 1) {
    if(cmd[0] == 's'){
      nvboard_update();
      single_cycle();
    }
    printf("[debug] ");
    // printf("%x\n",mem[13]);
  }
  #else
  int cnt = 7000;
  int num = 0;
  while(cnt -- > 0) {
    nvboard_update();
    single_cycle();
    // printf("cycle:%d\n",num++);
  }
  #endif


}
