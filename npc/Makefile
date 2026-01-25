VERILATOR = verilator

all:
	@echo "Write this Makefile by your self."
	$(VERILATOR) -cc --exe --build -j --top top vsrc/*.v csrc/main.cpp
sim:
	$(call git_commit, "sim RTL") # DO NOT REMOVE THIS LINE!!!
	@echo "Write this Makefile by your self."
	make all
	./obj_dir/Vtop

include ../Makefile
