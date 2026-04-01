#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
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

void init_elf(const char *elf_file){
    extract_elf_functions(elf_file,&funcs);
}