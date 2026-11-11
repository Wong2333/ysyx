#include "svdpi.h"
#include "VysyxSoCFull__Dpi.h"
#include "perf_names.h"

#ifdef NPC
#undef PERF_PREFIX
#define PERF_PREFIX "TOP.ysyxSoCFull.cpu."
#endif
#include <stdio.h>
#include <string.h>

// 性能计数器列表由 RTL 的 PerfCounter 声明自动生成（build/perf_names.h，见 scripts/gen_perf_names.py），
// 这里只需要按 scope 逐个读出打印。
void printPMU(){
    for (int i = 0; i < PERF_COUNT; i++) {
        char scope[256];
        snprintf(scope, sizeof(scope), "%s%s", PERF_PREFIX, perf_scope_names[i]);
        svScope s = svGetScopeFromName(scope);
        if (!s) {
            printf("Error: DPI Scope '%s' not found!\n", scope);
            continue;
        }
        svSetScope(s);

        long long data = 0;
        perf_read(&data);
        const char* name = strrchr(perf_scope_names[i], '.');
        name = name ? name + 1 : perf_scope_names[i];
        printf("[PERF] %s: %llu\n", name, (unsigned long long)data);
    }
}
