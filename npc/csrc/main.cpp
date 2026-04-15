#include <nvboard.h>
#include <VTop.h>
// #define DEBUG
static TOP_NAME top;

void nvboard_bind_all_pins(TOP_NAME* top);

static void single_cycle() {
  top.clock = !top.clock;
  top.eval();
  if(top.io_nextdata_n == 0){
    top.io_nextdata_n = 1;
  }
  if(top.io_ready){
    printf("data: %x\n",top.io_data);
    top.io_nextdata_n = 0;
  }
}
void reset(){
  top.io_nextdata_n = 1;
  top.clock = 0;
  top.io_clrn = 0;
  top.eval();
  top.clock = 1;
  top.eval();
  top.io_clrn = 1;
  top.clock = 0;
  top.eval();
}

int main(int argc,char**argv) {
  nvboard_bind_all_pins(&top);
  nvboard_init(); 
  reset();
  while(1) {
    nvboard_update();
    single_cycle();
  }
}
