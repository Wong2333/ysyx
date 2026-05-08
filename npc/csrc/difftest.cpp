#include <dlfcn.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <difftest.h>
#include <autoconfig.h>

#define DBG(fmt, ...) \
  do { printf("[DIFFTEST] " fmt "\n", ##__VA_ARGS__); fflush(stdout); } while(0)

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
uint32_t read_rf_reg(int addr);
uint32_t read_pc_reg();

static const char *reg_names[] = {
  "$0", "ra", "sp", "gp", "tp", "t0", "t1", "t2",
  "s0", "s1", "a0", "a1", "a2", "a3", "a4", "a5",
  "a6", "a7", "s2", "s3", "s4", "s5", "s6", "s7",
  "s8", "s9", "s10", "s11", "t3", "t4", "t5", "t6"
};

extern "C" void init_difftest(const char *ref_so_file, long img_sz) {
  DBG("init_difftest: ref_so = %s, img_sz = %ld", ref_so_file, img_sz);
  assert(ref_so_file != NULL);

  void *handle = dlopen(ref_so_file, RTLD_LAZY);
  if (!handle) {
    DBG("Cannot open '%s': %s", ref_so_file, dlerror());
    DBG("DISABLED");
    return;
  }
  DBG("dlopen OK");

  ref_difftest_memcpy = (ref_difftest_memcpy_t)dlsym(handle, "difftest_memcpy");
  assert(ref_difftest_memcpy); DBG("dlsym difftest_memcpy OK");

  ref_difftest_regcpy = (ref_difftest_regcpy_t)dlsym(handle, "difftest_regcpy");
  assert(ref_difftest_regcpy); DBG("dlsym difftest_regcpy OK");

  ref_difftest_exec = (ref_difftest_exec_t)dlsym(handle, "difftest_exec");
  assert(ref_difftest_exec); DBG("dlsym difftest_exec OK");

  ref_difftest_raise_intr = (ref_difftest_raise_intr_t)dlsym(handle, "difftest_raise_intr");
  assert(ref_difftest_raise_intr); DBG("dlsym difftest_raise_intr OK");

  ref_difftest_init_t ref_difftest_init = (ref_difftest_init_t)dlsym(handle, "difftest_init");
  assert(ref_difftest_init); DBG("dlsym difftest_init OK");

  DBG("Calling ref_difftest_init(0)...");
  fflush(stdout);
  ref_difftest_init(0);
  DBG("ref_difftest_init(0) done");

  /* Copy program image from NPC memory to REF memory */
  void *img_ptr = (uint8_t *)mem + CONFIG_START_ADDR;
  DBG("Copying %ld bytes to REF at 0x%08x", img_sz, CONFIG_START_ADDR);
  fflush(stdout);
  ref_difftest_memcpy(CONFIG_START_ADDR, img_ptr, img_sz, DIFFTEST_TO_REF);
  DBG("memcpy done");

  /* Copy NPC register state to REF */
  DBG("Reading NPC regs...");
  riscv32_CPU_state dut_state;
  for (int i = 0; i < 32; i++) {
    dut_state.gpr[i] = read_rf_reg(i);
  }
  dut_state.pc = read_pc_reg();
  DBG("NPC pc = 0x%08x", dut_state.pc);
  DBG("Copying regs to REF...");
  fflush(stdout);
  ref_difftest_regcpy(&dut_state, DIFFTEST_TO_REF);
  DBG("regcpy done");

  difftest_on = true;
  DBG("init done, difftest ON");
}

extern "C" void check_difftest(uint32_t pc, uint32_t npc) {
  if (!difftest_on) {
    static int once = 0;
    if (!once) { DBG("check_difftest skipped: difftest is OFF"); once = 1; }
    return;
  }

  static uint64_t check_cnt = 0;
  check_cnt++;
  if (check_cnt <= 5 || (check_cnt % 100000 == 0)) {
    DBG("[%lu] check_difftest pc=0x%08x npc=0x%08x", check_cnt, pc, npc);
  }

  /* Step REF by one instruction */
  DBG("[%lu] calling ref_difftest_exec(1)...", check_cnt);
  fflush(stdout);
  ref_difftest_exec(1);
  DBG("[%lu] ref_difftest_exec(1) done", check_cnt);

  /* Get REF state after execution */
  riscv32_CPU_state ref_state;
  ref_difftest_regcpy(&ref_state, DIFFTEST_TO_DUT);

  /* Read DUT (NPC) state */
  riscv32_CPU_state dut_state;
  for (int i = 0; i < 32; i++) {
    dut_state.gpr[i] = read_rf_reg(i);
  }
  dut_state.pc = read_pc_reg();

  DBG("[%lu] REF pc=0x%08x DUT pc=0x%08x", check_cnt, ref_state.pc, dut_state.pc);

  /* Compare GPRs (skip x0 which is always 0) */
  bool ok = true;
  for (int i = 1; i < 32; i++) {
    if (dut_state.gpr[i] != ref_state.gpr[i]) {
      DBG("Mismatch at pc = 0x%08x, %s: right = 0x%08x, wrong = 0x%08x",
             pc, reg_names[i], ref_state.gpr[i], dut_state.gpr[i]);
      ok = false;
    }
  }
  if (dut_state.pc != ref_state.pc) {
    DBG("Mismatch at pc = 0x%08x, pc: right = 0x%08x, wrong = 0x%08x",
           pc, ref_state.pc, dut_state.pc);
    ok = false;
  }

  if (!ok) {
    DBG("FAIL at pc = 0x%08x", pc);
    fflush(stdout);
    assert(0);
  }
}

extern "C" bool difftest_enabled() {
  return difftest_on;
}
