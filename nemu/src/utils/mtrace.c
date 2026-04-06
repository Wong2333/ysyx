#include <common.h>
#include <trace.h>
FILE *mtrace_fp = NULL;
char *TRACE_DIR = NULL;

void init_mtrace(){

}


bool mtrace_enable(paddr_t addr) {
  return MUXDEF(CONFIG_MTRACE, (addr >= CONFIG_MTRACE_START_ADDR) &&
      (addr <= CONFIG_MTRACE_END_ADDR), false);
}

void record_mtrace(MTRACE_MODE mode,paddr_t addr,int len,word_t data){
#ifdef CONFIG_MTRACE
    if (mtrace_enable(addr) && mtrace_fp != NULL) { 
        if(mode == READ_MTRACE)
            fprintf(mtrace_fp,"read memory: raddr=%u rlen=%u rdata=%u \n",addr,len, data); 
        else
            fprintf(mtrace_fp,"write memory: waddr=%u wlen=%u wdata=%u \n",addr,len, data); 
        fflush(mtrace_fp); 
    } 

#endif
}

#include <elf.h>

// 定义用于保存函数信息的结构体
typedef struct {
    char name[64];       // 函数名 (截断防溢出)
    uint32_t start_addr; // 函数起始地址
    uint32_t end_addr;   // 函数结束地址
    uint32_t size;       // 函数大小(字节数)
} ElfFunction;
ElfFunction *funcs = NULL;
/**
 * @brief 从32位ELF文件中提取所有函数及其地址范围
 * 
 * @param filename ELF文件路径
 * @param out_funcs 输出参数，返回动态分配的函数数组指针
 * @return int 提取到的函数数量，出错返回 -1
 */
int extract_elf_functions(const char *filename, ElfFunction **out_funcs) {
    FILE *fp = fopen(filename, "rb");
    if (!fp) {
        perror("Failed to open ELF file");
        return -1;
    }

    // 1. 读取并验证 ELF 头
    Elf32_Ehdr ehdr;
    if (fread(&ehdr, sizeof(Elf32_Ehdr), 1, fp) != 1) {
        perror("Failed to read ELF header");
        fclose(fp);
        return -1;
    }

    // 检查 ELF 魔数
    if (memcmp(ehdr.e_ident, ELFMAG, SELFMAG) != 0) {
        fprintf(stderr, "Error: Not a valid ELF file\n");
        fclose(fp);
        return -1;
    }

    // 检查是否为 32 位 (如果是64位需要换用 Elf64_Ehdr 等结构体)
    if (ehdr.e_ident[EI_CLASS] != ELFCLASS32) {
        fprintf(stderr, "Error: Not a 32-bit ELF file\n");
        fclose(fp);
        return -1;
    }

    // 2. 读取节区头表
    Elf32_Shdr *shdrs = malloc(ehdr.e_shnum * sizeof(Elf32_Shdr));
    if (!shdrs) { fclose(fp); return -1; }
    
    fseek(fp, ehdr.e_shoff, SEEK_SET);
    if (fread(shdrs, sizeof(Elf32_Shdr), ehdr.e_shnum, fp) != ehdr.e_shnum) {
        perror("Failed to read section headers");
        free(shdrs); fclose(fp); return -1;
    }

    // 3. 查找符号表 和 字符串表
    Elf32_Shdr *symtab_shdr = NULL;
    Elf32_Shdr *strtab_shdr = NULL;

    for (int i = 0; i < ehdr.e_shnum; i++) {
        if (shdrs[i].sh_type == SHT_SYMTAB) {
            symtab_shdr = &shdrs[i];
            // 符号表头中的 sh_link 指向对应的字符串表
            strtab_shdr = &shdrs[shdrs[i].sh_link];
            break;
        }
    }

    if (!symtab_shdr || !strtab_shdr) {
        fprintf(stderr, "Error: Could not find .symtab or .strtab (File might be stripped)\n");
        free(shdrs); fclose(fp); return -1;
    }

    // 4. 读取字符串表到内存
    char *strtab = malloc(strtab_shdr->sh_size);
    if (!strtab) { free(shdrs); fclose(fp); return -1; }
    
    fseek(fp, strtab_shdr->sh_offset, SEEK_SET);
    size_t len = fread(strtab, 1, strtab_shdr->sh_size, fp);
    if(len <=0 ) return -1;

    // 5. 读取符号表到内存
    int sym_count = symtab_shdr->sh_size / symtab_shdr->sh_entsize;
    Elf32_Sym *syms = malloc(symtab_shdr->sh_size);
    if (!syms) { free(strtab); free(shdrs); fclose(fp); return -1; }

    fseek(fp, symtab_shdr->sh_offset, SEEK_SET);
    len = fread(syms, symtab_shdr->sh_entsize, sym_count, fp);
    if(len <=0 ) return -1;

    // 6. 遍历符号，统计函数数量
    int func_count = 0;
    for (int i = 0; i < sym_count; i++) {
        // 判断是否为函数 (STT_FUNC) 且 有实际地址 (st_value != 0)
        if (ELF32_ST_TYPE(syms[i].st_info) == STT_FUNC && syms[i].st_value != 0) {
            func_count++;
        }
    }

    // 7. 分配内存并提取函数信息
    *out_funcs = malloc(func_count * sizeof(ElfFunction));
    if (!*out_funcs) { free(syms); free(strtab); free(shdrs); fclose(fp); return -1; }

    int idx = 0;
    for (int i = 0; i < sym_count; i++) {
        if (ELF32_ST_TYPE(syms[i].st_info) == STT_FUNC && syms[i].st_value != 0) {
            (*out_funcs)[idx].start_addr = syms[i].st_value;
            (*out_funcs)[idx].size = syms[i].st_size;
            (*out_funcs)[idx].end_addr = syms[i].st_value + syms[i].st_size;
            
            // 从字符串表提取名字，注意防止越界
            const char *name_ptr = strtab + syms[i].st_name;
            strncpy((*out_funcs)[idx].name, name_ptr, 63);
            (*out_funcs)[idx].name[63] = '\0';
            
            idx++;
        }
    }

    // 清理资源
    free(syms);
    free(strtab);
    free(shdrs);
    fclose(fp);

    return func_count;
}

FILE *ftrace_fp = NULL;

int funcs_count = 0;
#define FTRACE_STACK_MAX_DEPTH 1024

typedef struct {
    int func_idx;       // 保存 funcs 数组的下标，-1 表示未知函数
    uint32_t ret_addr;  // 记录预期的返回地址，用于识别尾调用
} FtraceFrame;

FtraceFrame ftrace_stack[FTRACE_STACK_MAX_DEPTH];
int ftrace_top = -1;

// --- 新增：根据 PC 获取函数在 funcs 数组中的下标 ---
int get_func_idx(uint32_t pc) {
    if (!funcs || funcs_count == 0) return -1;
    for (int i = 0; i < funcs_count; i++) {
        if (pc >= funcs[i].start_addr && pc < funcs[i].end_addr) {
            return i; // 返回下标
        }
    }
    return -1; // 没找到
}

/**
 * @brief 在执行 jal/jalr 时调用，记录 ftrace
 */
void record_ftrace(uint32_t pc, uint32_t target_pc, int rd, int rs1) {
#ifdef CONFIG_FTRACE
    if (!ftrace_fp) return;

    // 1. 识别函数调用 (rd == 1 即 ra)
    if (rd == 1) {
        int idx = get_func_idx(target_pc); // 拿下标，极快
        uint32_t next_pc = pc + 4; 

        // 检测是否为尾调用
        if (ftrace_top >= 0 && target_pc == ftrace_stack[ftrace_top].ret_addr) {
            // 尾调用：不增加栈深度，只替换栈顶的下标
            ftrace_stack[ftrace_top].func_idx = idx;
        } else {
            // 普通嵌套调用：压栈 (仅仅赋值一个 int，极快)
            if (ftrace_top < FTRACE_STACK_MAX_DEPTH - 1) {
                ftrace_top++;
                ftrace_stack[ftrace_top].func_idx = idx;
                ftrace_stack[ftrace_top].ret_addr = next_pc;
            }
        }

        // 打印 call (通过下标去取名字，只在真正需要打印时才去访问内存)
        const char *name = (idx != -1) ? funcs[idx].name : "???";
        fprintf(ftrace_fp, "%*s0x%08x: call [%s@0x%08x]\n", 
                ftrace_top * 2, "", pc, name, target_pc);
    }
    // 2. 识别函数返回 (rd == 0 且 rs1 == 1 或 5)
    else if (rd == 0 && (rs1 == 1 || rs1 == 5)) {
        const char *name = "???";
        int idx = -1;
        
        // 弹栈，拿到下标
        if (ftrace_top >= 0) {
            idx = ftrace_stack[ftrace_top].func_idx;
            name = (idx != -1) ? funcs[idx].name : "???";
            ftrace_top--;
        }

        // 打印 ret
        fprintf(ftrace_fp, "%*s0x%08x: ret  [%s]\n", 
                (ftrace_top + 1) * 2, "", pc, name);
    }
#endif
}

void init_trace(const char *trace_dir,const char *elf_file){
    char trace_path[1024*4];
    FILE *fp = NULL;
    // init mtrace
#ifdef CONFIG_MTRACE
    if(trace_dir){
        strcpy(trace_path,trace_dir);
        strcat(trace_path,"/mtrace.txt");
    }else{
        strcpy(trace_path,"./mtrace.txt");
    }
    fp = fopen(trace_path, "w");
    Assert(fp, "Can not open '%s'", trace_path);
    mtrace_fp = fp;
    Log("mtrace is written to ./mtrace.txt");
#endif
    // init ftrace
#ifdef CONFIG_FTRACE
    if(elf_file == NULL) return;
    if(trace_dir){
        strcpy(trace_path,trace_dir);
        strcat(trace_path,"/ftrace.txt");
    }else{
        strcpy(trace_path,"./ftrace.txt");
    }
    funcs_count = extract_elf_functions(elf_file,&funcs);

    fp = fopen(trace_path, "w");
    Assert(fp, "Can not open %s",trace_path);
    ftrace_fp = fp;

        // 初始化栈
    ftrace_top = -1; 
    
    Log("ftrace is written to ./ftrace.txt (Found %d functions)", funcs_count);
#endif
}