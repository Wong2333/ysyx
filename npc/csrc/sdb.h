#ifndef __SDB_H__
#define __SDB_H__
#include <stdint.h>
#include <stdbool.h>
#include <stdio.h>
uint32_t expr(char *e, bool *success);
bool add_wp(char *expr_str);
bool remove_wp(uint32_t no);
bool check_wp();
void info_wp();
int pmem_read(int addr);
void pmem_write(int addr, int data, char mask);
uint32_t read_rf_reg(int addr);
#endif
