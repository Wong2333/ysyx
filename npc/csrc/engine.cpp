#include <stdlib.h>
#include <string.h>
#include <getopt.h>
#include <time.h>
#include <cassert>
//#include <nvboard.h>
#include <VysyxSoCFull.h>
#include <autoconfig.h>

#ifdef CONFIG_ENABLE_FST_TRACE
#include <verilated_fst_c.h>
#endif
// #define DEBUG
#include <difftest.h>
#include <trace.h>
static TOP_NAME top;
#ifdef CONFIG_ENABLE_FST_TRACE
static VerilatedFstC* tfp = NULL;
#endif
struct timespec start, end;
bool check_wp();
//void nvboard_bind_all_pins(TOP_NAME* top);
void load_img(const char* path);

typedef enum {
  RUNNING,
  STOP
}ENGINE_STATE_TYPE;

ENGINE_STATE_TYPE STATE = STOP;
extern uint32_t *mem;
extern uint32_t img_size;
extern "C" void printCurrentInst();
uint32_t read_pc_reg();
uint32_t cycle = 0;
uint64_t dump_time = 0;

void my_assert(bool cond){
#ifdef CONFIG_ENABLE_FST_TRACE
    if(cond) return;
    tfp->close();
#else
    if(cond) return;
#endif
    assert(0);
}

void eval(){
    top.eval();
    #ifdef CONFIG_ENABLE_FST_TRACE
        if (tfp) tfp->dump(dump_time);
        dump_time++;
    #endif
}

static void single_cycle() {
    top.clock = ~top.clock;
    uint32_t current_pc = read_pc_reg();
    eval();
        
    uint32_t next_pc = read_pc_reg();
    trace_and_difftest(current_pc, next_pc);

    top.clock = ~top.clock;
    eval();
    //nvboard_update();
    if(check_wp()){
        STATE = STOP;
    }
}



void cpu_exec(int n){
    STATE = RUNNING;
    if(n > 0){
        for(int i = 0;i<n;i++){
            single_cycle();
            printCurrentInst();
            if(STATE == STOP) break;
        }
    }else{
        while(1){
            single_cycle();
            // cycle++;
            // if(cycle == 20) {tfp->close(); exit(0);}
            if(STATE == STOP) break;
        }
    }
}
void init_sdb();

void init_mem(const char* img_path){
    if(img_path != NULL)
        load_img(img_path);
}

void init_cpu(){
    clock_gettime(CLOCK_PROCESS_CPUTIME_ID, &start);
    //eval();
    top.reset = 1;
    top.clock = 0;
    eval();

    for(int i = 0; i < 15; i++) {
        // 时钟上升沿
        top.clock = 1;
        eval();
        // 时钟下降沿
        top.clock = 0;
        eval();

    }
    top.reset = 0;
    // top.eval(); // 立即更新组合逻辑状态
}


// void init_nvboard(){
//     nvboard_bind_all_pins(&top);
//     nvboard_init();
// }

void init_engine(int argc, char **argv) {
    Verilated::commandArgs(argc, argv);
    const char *img_file = NULL;
    const char *diff_so_file = NULL;
    const char *elf_file = NULL;
    const char *trace_dir = NULL;
    bool batch_mode = false;

    const struct option table[] = {
        {"batch", no_argument, NULL, 'b'},
        {"image", required_argument, NULL, 'i'},
        {"diff",  required_argument, NULL, 'd'},
        {"trace", required_argument, NULL, 't'},
        {"elf",   required_argument, NULL, 'e'},
        {"help",  no_argument,       NULL, 'h'},
        {0, 0, NULL, 0},
    };
    int o;
    while ((o = getopt_long(argc, argv, "-bhi:d:t:e:", table, NULL)) != -1) {
        switch (o) {
            case 'b': batch_mode = true; break;
            case 'i': img_file = optarg; break;
            case 'd': diff_so_file = optarg; break;
            case 't': trace_dir = optarg; break;
            case 'e': elf_file = optarg; break;
            case 1: img_file = optarg; break;
            case 'h':
                printf("Usage: %s [OPTION...] IMAGE\n\n", argv[0]);
                printf("\t-b,--batch           batch mode (disable SDB)\n");
                printf("\t-i,--image=IMAGE     program image\n");
                printf("\t-d,--diff=REF_SO     DiffTest reference SO\n");
                printf("\t-t,--trace=TRACE_DIR trace output directory\n");
                printf("\t-e,--elf=ELF         ELF file for ftrace\n");
                printf("\n");
                exit(0);
            default:
                exit(1);
        }
    }

    //init_nvboard();
#ifdef CONFIG_ENABLE_SDB
    if (!batch_mode) {
        init_sdb();
    }
#endif
    if (img_file)
        init_mem(img_file);
    else
        init_mem(NULL);
    init_cpu();
#ifdef CONFIG_ENABLE_FST_TRACE
    // initialize FST waveform dump
    Verilated::traceEverOn(true);
    tfp = new VerilatedFstC;
    top.trace(tfp, 99);  // trace 99 levels of hierarchy
    tfp->open("waveform.fst");
#endif
    init_trace(trace_dir, elf_file ? elf_file : img_file);
#ifdef CONFIG_ENABLE_DIFFTEST
    {
        const char *ref_so = diff_so_file;
        if (ref_so == NULL)
            ref_so = getenv("NEMU_REF_SO");
        if (ref_so == NULL)
            ref_so = "../nemu/build/riscv32-nemu-interpreter-so";
        init_difftest(ref_so, img_size);
    }
#endif
    STATE = STOP;
}

void sdb_mainloop();

void start_engine(){
    while(1) {
        sdb_mainloop();
    }
}
