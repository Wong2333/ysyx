#include <nvboard.h>
#include <Vtop.h>
#include <unistd.h>

#define WAIT 300000000
static TOP_NAME top;

void nvboard_bind_all_pins(TOP_NAME* top);

int n = 1;
static void eval(){
 
  if(n == 0b10000000){
    n = 1;
  }else{
    n = n << 1;
  }
  printf("n = %d\n",n);
  top.n = n;
  top.eval();
}

int main() {
  nvboard_bind_all_pins(&top);
  nvboard_init();


  int i = 0;
  while(1) {
    nvboard_update();
    if(i % WAIT == 0) eval();
    i++;
  }
}
