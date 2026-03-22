#include "svdpi.h"
#include "Vtop__Dpi.h"
#include <stdlib.h>
#include <stdio.h>
// DPI-C 函数实现
void ebreak() {
    printf("END\n");
    exit(0);
}

void invalid_instruction(int pc,int inst){
    printf("invalid_instruction: 0x%x at 0x%x\n",inst,pc);
    exit(1);
}