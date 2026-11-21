# rt-thread-am 补丁（`git format-patch`）

基准：`origin/master`。从上游到本地 `HEAD` 的**每个 commit 一个 patch**。

## 生成

```bash
cd rt-thread-am
git format-patch origin/master -o ../patch/rt-thread-am
```

## 应用

```bash
cd rt-thread-am
git am /path/to/ysyx-workbench/patch/rt-thread-am/*.patch
```

## 内容

- npc 平台适配：`extra-npc.ld` + `Makefile` 按 ARCH 选链接脚本、`context.c` 上下文切换、
  `uart.c` 预置命令、`init.c` 微调（详见提交信息）；
- 当前 **2 个**补丁、总大小约 16KB。

> 一键拉取 + 打补丁：`./patch/setup.sh`。
