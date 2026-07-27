#ifndef __UTILS_H__
#define __UTILS_H__
#include <cassert>
static void my_assert(bool cond){
#ifdef CONFIG_ENABLE_FST_TRACE
    if(cond) return;
    tfp->close();
#else
    if(cond) return;
#endif
    assert(0);
}

#endif