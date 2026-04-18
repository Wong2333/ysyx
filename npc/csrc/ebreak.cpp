#include "svdpi.h"
#include "VTop__Dpi.h"
#include <stdlib.h>
#include <stdio.h>
#include "VTop.h"               // Verilator 生成的顶层模块类

uint32_t read_rf_reg(int addr) {
    const char* scope_name = "TOP.Top.RFS_u"; 
    
    svScope scope = svGetScopeFromName(scope_name);
    
    if (!scope) {
        printf("Error: DPI Scope '%s' not found! Check your Verilog instance name.\n", scope_name);
        return -1;
    }
    svSetScope(scope);

    // 3. 调用 Verilog 导出的 task
    int data = 0;
    read_reg(addr, &data); 
    return (uint32_t)data;
}

// DPI-C 函数实现
void ebreak() {
    uint32_t status = read_rf_reg(10);
    if(status == 0){
        printf("HIT GOOD TRAP\n");
        exit(0);
    }else{
        printf("%x\n",status);
        printf("HIT BAD TRAP\n");
        exit(1);
    }
}

void invalid_instruction(int pc,int inst){
    printf("invalid_instruction: 0x%x at 0x%x\n",(uint32_t)inst,(uint32_t)pc);
    exit(1);
}