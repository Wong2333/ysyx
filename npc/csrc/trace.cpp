#include <dlfcn.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <elf.h>
#include <capstone/capstone.h>
#include "trace.h"

/* ===================== Capstone (itrace) ===================== */
static void *cs_handle = NULL;
static size_t (*cs_disasm_dl)(csh handle, const uint8_t *code,
    size_t code_size, uint64_t address, size_t count, cs_insn **insn) = NULL;
static void (*cs_free_dl)(cs_insn *insn, size_t count) = NULL;
static csh cs_h;
static bool disasm_ok = false;

/* Ring buffer for last 32 instructions */
static char iringbuf[32][128];
static uint32_t ibuf_head = 0, ibuf_tail = 0;

/* ===================== Mtrace ===================== */
static FILE *mtrace_fp = NULL;

/* ===================== Ftrace ===================== */
#ifdef CONFIG_ENABLE_FTRACE
static FILE *ftrace_fp = NULL;

typedef struct {
  char name[64];
  uint32_t start_addr;
  uint32_t end_addr;
} ElfFunction;

static ElfFunction *funcs = NULL;
static int funcs_count = 0;

#define FTRACE_STACK_DEPTH 1024
typedef struct {
  int func_idx;
  uint32_t ret_addr;
} FtraceFrame;
static FtraceFrame ftrace_stack[FTRACE_STACK_DEPTH];
static int ftrace_top = -1;
#endif

/* ===================== itrace init and disasm ===================== */
static void init_disasm(void) {
  const char *cs_lib = "tools/capstone/repo/libcapstone.so.5";

  cs_handle = dlopen(cs_lib, RTLD_LAZY);
  if (!cs_handle) {
    printf("ITrace: Cannot open '%s'. ITrace disabled.\n", cs_lib);
    return;
  }

  cs_err (*cs_open_dl)(cs_arch arch, cs_mode mode, csh *handle);
  cs_open_dl = (cs_err (*)(cs_arch, cs_mode, csh *))dlsym(cs_handle, "cs_open");
  assert(cs_open_dl);

  cs_disasm_dl = (size_t (*)(csh, const uint8_t *, size_t, uint64_t, size_t, cs_insn **))
                 dlsym(cs_handle, "cs_disasm");
  assert(cs_disasm_dl);

  cs_free_dl = (void (*)(cs_insn *, size_t))dlsym(cs_handle, "cs_free");
  assert(cs_free_dl);

  int ret = cs_open_dl(CS_ARCH_RISCV, CS_MODE_RISCV32, &cs_h);
  assert(ret == CS_ERR_OK);
  disasm_ok = true;
}

static void disassemble(char *str, int size, uint64_t pc, uint8_t *code, int nbyte) {
  if (!disasm_ok) {
    snprintf(str, size, "0x%08x", *(uint32_t *)code);
    return;
  }
  cs_insn *insn;
  size_t count = cs_disasm_dl(cs_h, code, nbyte, pc, 0, &insn);
  if (count == 0) {
    snprintf(str, size, "0x%08x", *(uint32_t *)code);
    return;
  }
  int ret = snprintf(str, size, "%s", insn->mnemonic);
  if (insn->op_str[0] != '\0') {
    snprintf(str + ret, size - ret, "\t%s", insn->op_str);
  }
  cs_free_dl(insn, count);
}

static void push_itrace(const char *trace) {
  strcpy(iringbuf[ibuf_tail], trace);
  ibuf_tail = (ibuf_tail + 1) % 32;
  if (ibuf_tail == ibuf_head) {
    ibuf_head = (ibuf_head + 1) % 32;
  }
}

void print_itrace(void) {
  printf("itrace:\n");
  uint32_t h = ibuf_head;
  while (h != ibuf_tail) {
    printf("%s\n", iringbuf[h]);
    h = (h + 1) % 32;
  }
}

/* ===================== ELF parsing (ftrace) ===================== */
#ifdef CONFIG_ENABLE_FTRACE
static int extract_elf_functions(const char *filename) {
  FILE *fp = fopen(filename, "rb");
  if (!fp) return -1;

  Elf32_Ehdr ehdr;
  if (fread(&ehdr, sizeof(Elf32_Ehdr), 1, fp) != 1) {
    fclose(fp); return -1;
  }
  if (memcmp(ehdr.e_ident, ELFMAG, SELFMAG) != 0) {
    fclose(fp); return -1;
  }

  Elf32_Shdr *shdrs = (Elf32_Shdr *)malloc(ehdr.e_shnum * sizeof(Elf32_Shdr));
  if (!shdrs) { fclose(fp); return -1; }
  fseek(fp, ehdr.e_shoff, SEEK_SET);
  fread(shdrs, sizeof(Elf32_Shdr), ehdr.e_shnum, fp);

  Elf32_Shdr *symtab = NULL, *strtab = NULL;
  for (int i = 0; i < ehdr.e_shnum; i++) {
    if (shdrs[i].sh_type == SHT_SYMTAB) {
      symtab = &shdrs[i];
      strtab = &shdrs[shdrs[i].sh_link];
      break;
    }
  }
  if (!symtab || !strtab) {
    free(shdrs); fclose(fp); return -1;
  }

  char *strtab_buf = (char *)malloc(strtab->sh_size);
  fseek(fp, strtab->sh_offset, SEEK_SET);
  fread(strtab_buf, 1, strtab->sh_size, fp);

  int sym_count = symtab->sh_size / symtab->sh_entsize;
  Elf32_Sym *syms = (Elf32_Sym *)malloc(symtab->sh_size);
  fseek(fp, symtab->sh_offset, SEEK_SET);
  fread(syms, symtab->sh_entsize, sym_count, fp);

  int count = 0;
  for (int i = 0; i < sym_count; i++) {
    if (ELF32_ST_TYPE(syms[i].st_info) == STT_FUNC && syms[i].st_value != 0)
      count++;
  }

  funcs = (ElfFunction *)malloc(count * sizeof(ElfFunction));
  int idx = 0;
  for (int i = 0; i < sym_count; i++) {
    if (ELF32_ST_TYPE(syms[i].st_info) == STT_FUNC && syms[i].st_value != 0) {
      funcs[idx].start_addr = syms[i].st_value;
      funcs[idx].end_addr = syms[i].st_value + syms[i].st_size;
      strncpy(funcs[idx].name, strtab_buf + syms[i].st_name, 63);
      funcs[idx].name[63] = '\0';
      idx++;
    }
  }

  free(syms);
  free(strtab_buf);
  free(shdrs);
  fclose(fp);
  return count;
}

static int get_func_idx(uint32_t pc) {
  if (!funcs) return -1;
  for (int i = 0; i < funcs_count; i++) {
    if (pc >= funcs[i].start_addr && pc < funcs[i].end_addr)
      return i;
  }
  return -1;
}
#endif

/* ===================== record functions ===================== */
void record_mtrace(RECORD_MODE mode, uint32_t addr, int len, uint32_t data) {
#ifdef CONFIG_ENABLE_MTRACE
  if (!mtrace_fp) return;
  if (mode == READ_TRACE)
    fprintf(mtrace_fp, "read  memory: addr=0x%08x len=%d data=0x%08x\n", addr, len, data);
  else
    fprintf(mtrace_fp, "write memory: addr=0x%08x len=%d data=0x%08x\n", addr, len, data);
#endif
}

void record_ftrace(uint32_t pc, uint32_t dnpc, uint32_t inst) {
#ifdef CONFIG_ENABLE_FTRACE
  if (!ftrace_fp) return;

  uint32_t opcode = inst & 0x7f;
  uint32_t next_pc = pc + 4;

  if (opcode == 0x6f) { /* jal */
    int rd = (inst >> 7) & 0x1f;
    if (rd == 1) { /* call */
      int idx = get_func_idx(dnpc);
      /* check tail call */
      if (ftrace_top >= 0 && dnpc == ftrace_stack[ftrace_top].ret_addr) {
        ftrace_stack[ftrace_top].func_idx = idx;
      } else {
        if (ftrace_top < FTRACE_STACK_DEPTH - 1) {
          ftrace_top++;
          ftrace_stack[ftrace_top].func_idx = idx;
          ftrace_stack[ftrace_top].ret_addr = next_pc;
        }
      }
      const char *name = (idx >= 0) ? funcs[idx].name : "???";
      fprintf(ftrace_fp, "%*s0x%08x: call [%s@0x%08x]\n",
              ftrace_top * 2, "", pc, name, dnpc);
    }
  } else if (opcode == 0x67) { /* jalr */
    int rd = (inst >> 7) & 0x1f;
    int rs1 = (inst >> 15) & 0x1f;
    if (rd == 1) { /* call */
      int idx = get_func_idx(dnpc);
      if (ftrace_top >= 0 && dnpc == ftrace_stack[ftrace_top].ret_addr) {
        ftrace_stack[ftrace_top].func_idx = idx;
      } else {
        if (ftrace_top < FTRACE_STACK_DEPTH - 1) {
          ftrace_top++;
          ftrace_stack[ftrace_top].func_idx = idx;
          ftrace_stack[ftrace_top].ret_addr = next_pc;
        }
      }
      const char *name = (idx >= 0) ? funcs[idx].name : "???";
      fprintf(ftrace_fp, "%*s0x%08x: call [%s@0x%08x]\n",
              ftrace_top * 2, "", pc, name, dnpc);
    } else if ((rd == 0 && (rs1 == 1 || rs1 == 5))) { /* ret */
      const char *name = "???";
      int idx = -1;
      if (ftrace_top >= 0) {
        idx = ftrace_stack[ftrace_top].func_idx;
        name = (idx >= 0) ? funcs[idx].name : "???";
        ftrace_top--;
      }
      fprintf(ftrace_fp, "%*s0x%08x: ret  [%s]\n",
              (ftrace_top + 1) * 2, "", pc, name);
    }
  }
#endif
}

/* ===================== Main trace function (called from cpu_exec) ===================== */
void trace_and_difftest(uint32_t pc, uint32_t inst, uint32_t dnpc) {
#ifdef CONFIG_ENABLE_ITRACE
  static char logbuf[256];
  char buf[128];
  uint8_t code[4] = {
    (uint8_t)(inst >> 0),
    (uint8_t)(inst >> 8),
    (uint8_t)(inst >> 16),
    (uint8_t)(inst >> 24)
  };
  disassemble(buf, sizeof(buf), pc, code, 4);
  snprintf(logbuf, sizeof(logbuf), "0x%08x: %-8s %s", pc, buf, "");
  push_itrace(logbuf);
  puts(logbuf);
#endif

  /* ftrace: always check (guarded inside record_ftrace) */
  record_ftrace(pc, dnpc, inst);
}

/* ===================== Init ===================== */
void init_trace(const char *trace_dir, const char *elf_file) {
  printf("Trace init: dir=%s, elf=%s\n", trace_dir ? trace_dir : "NULL", elf_file ? elf_file : "NULL");

#ifdef CONFIG_ENABLE_ITRACE
  init_disasm();
#endif

#ifdef CONFIG_ENABLE_MTRACE
  {
    char path[4096];
    if (trace_dir)
      snprintf(path, sizeof(path), "%s/mtrace.txt", trace_dir);
    else
      snprintf(path, sizeof(path), "mtrace.txt");
    mtrace_fp = fopen(path, "w");
    if (mtrace_fp)
      printf("MTrace: writing to %s\n", path);
  }
#endif

#ifdef CONFIG_ENABLE_FTRACE
  {
    char path[4096];
    if (trace_dir)
      snprintf(path, sizeof(path), "%s/ftrace.txt", trace_dir);
    else
      snprintf(path, sizeof(path), "ftrace.txt");
    ftrace_fp = fopen(path, "w");
    if (ftrace_fp)
      printf("FTrace: writing to %s\n", path);
  }
  if (elf_file) {
    funcs_count = extract_elf_functions(elf_file);
    printf("FTrace: found %d functions from %s\n", funcs_count, elf_file);
  }
#endif
}
