# 与 riscv32e-ysyxSoC.mk 完全等价，只是平台名用小写。
# CI 讲义里给的是 `make ARCH=riscv32e-ysyxsoc run`，而 am/Makefile 的 ARCHS
# 白名单是 `ls scripts/*.mk`，所以这里必须有一个同名文件才能通过 ARCH 检查。
include $(AM_HOME)/scripts/riscv32e-ysyxSoC.mk
