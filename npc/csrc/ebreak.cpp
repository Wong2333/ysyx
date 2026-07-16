#include "svdpi.h"
#include "VysyxSoCFull__Dpi.h"
#include <stdlib.h>
#include <stdio.h>
#include "VysyxSoCFull.h"

uint32_t read_rf_reg(int addr) {
    const char* scope_name = "ysyxSoCFull"; 
    
    svScope scope = svGetScopeFromName(scope_name);
    
    if (!scope) {
        printf("Error: DPI Scope '%s' not found! Check your Verilog instance name.\n", scope_name);
        return -1;
    }
    svSetScope(scope);

    //verilog 导出的 task
    int data = 0;
    read_reg(addr, &data); 
    return (uint32_t)data;
}

uint32_t read_pc_reg() {
    const char* scope_name = "ysyxSoCFull"; 
    
    svScope scope = svGetScopeFromName(scope_name);
    
    if (!scope) {
        printf("Error: DPI Scope '%s' not found! Check your Verilog instance name.\n", scope_name);
        return -1;
    }
    svSetScope(scope);

    //verilog 导出的 task
    int data = 0;
    read_pc(&data); 
    return (uint32_t)data;
}

const char *regs[] = {
  "$0", "ra", "sp", "gp", "tp", "t0", "t1", "t2",
  "s0", "s1", "a0", "a1", "a2", "a3", "a4", "a5",
  "a6", "a7", "s2", "s3", "s4", "s5", "s6", "s7",
  "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"
};

uint32_t isa_reg_str2val(const char *s, bool *success) {
  if(strcmp("pc",s)==0){
    *success=true;
    return read_pc_reg();
  }
  for(int i = 0;i < 32;i++){
    if(strcmp(regs[i],s)==0){
      *success=true;
      return read_rf_reg(i);
    }
  }
  *success=false;
  return 0;
}
void isa_reg_display(){
    for(int i = 0;i<32;i++){
        printf("%3s  %x\n",regs[i],read_rf_reg(i));
    }
    printf("pc   %x\n",read_pc_reg());
}
// DPI-C 函数实现
void ebreak() {
    uint32_t status = read_rf_reg(10);
    if(status == 0){
        printf("HIT GOOD TRAP\n");
        exit(0);
    }else{
        printf("HIT BAD TRAP\n");
        exit(1);
    }
}
void my_assert(bool cond);
void invalid_instruction(int pc,int inst){
    printf("invalid_instruction: 0x%x at 0x%x\n",(uint32_t)inst,(uint32_t)pc);
    my_assert(0);
}