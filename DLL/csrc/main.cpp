#include <nvboard.h>
#include <Vtop.h>
#include <unistd.h>

static TOP_NAME top;

void nvboard_bind_all_pins(TOP_NAME* top);

int cycle=0;
static void eval(){
  top.sys_clk = top.sys_clk?0:1;
  top.eval();
  cycle++;
}

int main() {
  nvboard_bind_all_pins(&top);
  nvboard_init();

  top.sys_clk=0;
  while(1) {
    nvboard_update();
    eval();
  }
}
