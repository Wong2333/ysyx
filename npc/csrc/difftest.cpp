#include <dlfcn.h>
#include "svdpi.h"
#include "VysyxSoCFull__Dpi.h"
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <difftest.h>
#include <autoconfig.h>
#include <trace.h>
#include <sdb.h>
#include <utils.h>

/* Function pointer types for the REF shared library API */
typedef void (*ref_difftest_memcpy_t)(uint32_t addr, void *buf, size_t n, bool direction);
typedef void (*ref_difftest_regcpy_t)(void *dut, bool direction);
typedef void (*ref_difftest_exec_t)(uint64_t n);
typedef void (*ref_difftest_raise_intr_t)(uint64_t NO);
typedef void (*ref_difftest_init_t)(int port);

/* CPU state matching NEMU's riscv32_CPU_state: 32 GPRs + PC */
typedef struct {
  uint32_t gpr[32];
  uint32_t pc;
} riscv32_CPU_state;

enum { DIFFTEST_TO_DUT, DIFFTEST_TO_REF };

/* Function pointers loaded from REF shared library */
static ref_difftest_memcpy_t ref_difftest_memcpy = NULL;
static ref_difftest_regcpy_t ref_difftest_regcpy = NULL;
static ref_difftest_exec_t ref_difftest_exec = NULL;
static ref_difftest_raise_intr_t ref_difftest_raise_intr = NULL;

static bool difftest_on = false;

/* NPC memory and register access */
extern uint32_t *mem;
extern uint32_t img_size;
extern uint32_t flash[1024];
extern uint32_t img_size;
uint32_t read_rf_reg(int addr);
uint32_t read_pc_reg();

static const char *reg_names[] = {
  "$0", "ra", "sp", "gp", "tp", "t0", "t1", "t2",
  "s0", "s1", "a0", "a1", "a2", "a3", "a4", "a5",
  "a6", "a7", "s2", "s3", "s4", "s5", "s6", "s7",
  "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"
};

void init_difftest(const char *ref_so_file, long img_sz) {
  assert(ref_so_file != NULL);

  void *handle = dlopen(ref_so_file, RTLD_LAZY);
  if (!handle) {
    printf("Difftest: Cannot open '%s'. Difftest disabled.\n", ref_so_file);
    fflush(stdout);
    return;
  }

  ref_difftest_memcpy = (ref_difftest_memcpy_t)dlsym(handle, "difftest_memcpy");
  assert(ref_difftest_memcpy);

  ref_difftest_regcpy = (ref_difftest_regcpy_t)dlsym(handle, "difftest_regcpy");
  assert(ref_difftest_regcpy);

  ref_difftest_exec = (ref_difftest_exec_t)dlsym(handle, "difftest_exec");
  assert(ref_difftest_exec);

  ref_difftest_raise_intr = (ref_difftest_raise_intr_t)dlsym(handle, "difftest_raise_intr");
  assert(ref_difftest_raise_intr);

  ref_difftest_init_t ref_difftest_init = (ref_difftest_init_t)dlsym(handle, "difftest_init");
  assert(ref_difftest_init);

  printf("\033[32mDifferential testing: ON\033[0m\n");
  fflush(stdout);

  ref_difftest_init(0);

  void *img_ptr = (uint8_t *)flash;
  ref_difftest_memcpy(CONFIG_START_ADDR, img_ptr, img_size, DIFFTEST_TO_REF);

  riscv32_CPU_state dut_state;
  // RV32E: RTL 只有 16 个寄存器，高 16 个清零且不再更新（软件不写 x16-x31）
  memset(&dut_state, 0, sizeof(dut_state));
  for (int i = 0; i < 16; i++) {
    dut_state.gpr[i] = read_rf_reg(i);
  }
  dut_state.pc = read_pc_reg();
  ref_difftest_regcpy(&dut_state, DIFFTEST_TO_REF);

  difftest_on = true;

}

void my_assert(bool cond);
uint32_t mem_read(uint32_t addr);
bool in_mem(uint32_t addr);
#include<npc-memory.h>

// 读取 RTL 提交指令的访存信息（DifftestHelper 黑盒，执行时刻的真实地址）
static bool read_difftest_mem_info(int *valid, uint32_t *addr) {
  const char* scope_name = "TOP.ysyxSoCFull.asic.cpu.cpu.difftest_helper_u";
  svScope scope = svGetScopeFromName(scope_name);
  if (!scope) {
    printf("Error: DPI Scope '%s' not found!\n", scope_name);
    return false;
  }
  svSetScope(scope);
  int v = 0;
  int a = 0;
  difftest_mem_info(&v, &a);
  *valid = v;
  *addr = (uint32_t)a;
  return true;
}

static bool is_skip_ref(uint32_t pc){
  int valid = 0;
  uint32_t addr = 0;
  if (!read_difftest_mem_info(&valid, &addr)) return false;
  if (!valid) return false;
  return !in_mem(addr);
}

void check_difftest(uint32_t pc) {
  if (!difftest_on) return;

  riscv32_CPU_state dut_state;
  // RV32E: 只读低 16 个寄存器，高 16 个保持 0
  memset(&dut_state, 0, sizeof(dut_state));
  for (int i = 0; i < 16; i++) {
    dut_state.gpr[i] = read_rf_reg(i);
  }
  dut_state.pc = read_pc_reg();

  if (is_skip_ref(pc)) {
    ref_difftest_regcpy(&dut_state, DIFFTEST_TO_REF);
    return;
  }
  ref_difftest_exec(1);

  riscv32_CPU_state ref_state;
  ref_difftest_regcpy(&ref_state, DIFFTEST_TO_DUT);

  // check
  // static uint32_t cnt = 0;
  // printf("difftesting...%u\n",cnt++);
  bool ok = true;
  for (int i = 1; i < 32; i++) {
    if (dut_state.gpr[i] != ref_state.gpr[i]) {
      printf("Difftest: Mismatch at pc = 0x%08x, %s: right = 0x%08x, wrong = 0x%08x\n",
             pc, reg_names[i], ref_state.gpr[i], dut_state.gpr[i]);
      ok = false;
    }
  }
  if (dut_state.pc != ref_state.pc) {
    printf("Difftest: Mismatch at pc = 0x%08x, pc: right = 0x%08x, wrong = 0x%08x\n",
           pc, ref_state.pc, dut_state.pc);
    ok = false;
  }

  if (!ok) {
    printf("Difftest: FAIL at pc = 0x%08x\n", pc);
    fflush(stdout);

    my_assert(0);
  }
}

bool difftest_enabled() {
  return difftest_on;
}

static bool commit = false;

extern "C" void difftest_commit(){
  commit = true;
}

extern "C" void difftest_reset(){

}