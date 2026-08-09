#include "svdpi.h"
#include "VysyxSoCFull__Dpi.h"
#include <stdio.h>

char *perf_name[128] = {
    "IFU fetch instruction",
    "LSU get data",
    "LSU access mem latency",
    "EXU finished",
    "IDU jump instruction",
    "IDU brench instruction",
    "IDU load instruction",
    "IDU calculate instruction",
    "IDU csr instruction",
    "IDU store instruction",
    "IFU get inst cycles",
    "LSU wait free cycles",
    "LSU wait resp cycles",
    "PIPE idle cycles",
    "WBU commit instruction",
    "icache hit"
};

// 从 RTL 的 PerfCounterArray 一次性读出所有计数器（DPI-C export task perf_read）
void printPMU(){
    const char* scope_name = "TOP.ysyxSoCFull.asic.cpu.cpu.perf_u";
    svScope scope = svGetScopeFromName(scope_name);
    if (!scope) {
        printf("Error: DPI Scope '%s' not found! Check your Verilog instance name.\n", scope_name);
        return;
    }
    svSetScope(scope);

    for (int id = 0; id < 16; id++) {
        long long data = 0;
        perf_read(id, &data);
        printf("[PERF] id: %d %s counter:   %llu\n", id, perf_name[id], (unsigned long long)data);
    }
}
