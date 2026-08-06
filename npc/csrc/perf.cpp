#include <stdio.h>
#include <unordered_map>

std::unordered_map<int, int> perf;
char *perf_name[128] = {
    "IFU fetch instruction",
    "LSU get data",
    "LSU access mem latency",
    "EXU finished",
    "IDU load instruction",
    "IDU calculate instruction",
    "IDU csr instruction",
    "IDU store instruction"
};

extern "C" void perf_inc(int id, int cnt){
    perf[id] += cnt;

}

void printPMU(){
    for(int id = 0; id<7;id ++){
        printf("[PERF] id: %d %s counter:   %llu\n", id, perf_name[id] ,perf[id]);
    }
}