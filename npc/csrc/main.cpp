#include <nvboard.h>
#include <VTop.h>
// #define DEBUG
static TOP_NAME top;

void nvboard_bind_all_pins(TOP_NAME* top);
extern void load_img(const char* path);

static void single_cycle() {
  printf("pc:%x inst:%x\n",top.io_pc,top.io_inst);
  top.clock = ~top.clock; 
  top.eval();
  top.clock = ~top.clock; 
  top.eval();
}
void reset(const char *path){
  if(path != NULL)
    load_img(path);
  top.clock = 0;
  top.reset = 1;
  top.eval();
  top.clock = 1;
  top.eval();
  top.reset = 0;
  top.clock = 0;
  top.eval();
}

int main(int argc,char**argv) {
  nvboard_bind_all_pins(&top);
  nvboard_init(); 
  if(argc >= 2)
    reset(argv[1]);
  else reset(NULL);
  int n = 8000;
  while(n-- >0) {
    nvboard_update();
    single_cycle();
  }
}
