#include <nvboard.h>
#include <VTop.h>
// #define DEBUG
static TOP_NAME top;

void nvboard_bind_all_pins(TOP_NAME* top);

static void single_cycle() {
  top.eval();
}
void reset(char*path){
  top.clock = 0;
  top.io_enable = 1;
  top.eval();
}

int main(int argc,char**argv) {
  nvboard_bind_all_pins(&top);
  nvboard_init(); 
  int num = 0;
  reset();
  while(1) {
    nvboard_update();
    single_cycle();
  }
}
