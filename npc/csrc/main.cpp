#include <nvboard.h>
#include <VTop.h>
// #define DEBUG
static TOP_NAME top;

void nvboard_bind_all_pins(TOP_NAME* top);

static void single_cycle() {
  top.clock = !top.clock;
  top.eval();
  printf("pc:%x inst:%x\n",top.io_pc,top.io_inst);
}
void reset(){
  top.clock = 0;
  top.io_rst = 0;
  top.eval();
  top.clock = 1;
  top.eval();
  top.io_rst = 0;
  top.clock = 0;
  top.eval();
}

int main(int argc,char**argv) {
  nvboard_bind_all_pins(&top);
  nvboard_init(); 
  // reset();
  top.clock=0;
  top.eval();
  int n = 100;
  while(n-- > 0) {
    nvboard_update();
    single_cycle();
  }
}
