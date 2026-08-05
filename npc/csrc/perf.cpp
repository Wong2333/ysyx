#include <stdio.h>
#include <unordered_map>

std::unordered_map<int, int> perf;
char *perf_name[128] = {
    "IFU fetch instruction",
    "LSU get data",
    "EXU finished",
    "IDU load instruction"
};

// // 假设 perf 里已经有数据了...

// // 遍历
// for (const auto& pair : perf) {
//     // pair.first  是键
//     // pair.second 是值
//     std::cout << "ID: " << pair.first << ", Count: " << pair.second << std::endl;
// }

extern "C" void perf_inc(int id, int cnt){
    perf[id] += cnt;

}

void printfPMU(){
    for (const auto& [id, count] : perf) {
        printf("[PERF] %s counter:   %llu\n", perf_name[id] ,count);
    }
}