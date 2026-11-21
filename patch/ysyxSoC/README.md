# ysyxSoC 补丁（`git format-patch`）

基准：`origin/ysyx6`（当前 `bcdcbc3e`）。从上游到本地 `HEAD` 的**每个 commit 一个 patch**，
按 `0001-…` 顺序应用即可复现本地状态。

## 生成

```bash
cd ysyxSoC
git format-patch origin/ysyx6 -o ../patch/ysyxSoC
```

## 应用

```bash
# CI / 手动
cd ysyxSoC
git am /path/to/ysyx-workbench/patch/ysyxSoC/*.patch
```

## 说明

- `patch/firtool/` 下的 firtool 二进制（约 36MB）已从分支历史移除并加入 `.gitignore`，
  改由 `make verilog` 里的 `patch/update-firtool.sh` 自动下载；
- 当前补丁总大小约 160KB（要求 < 1MB）；
- 一键拉取 + 打补丁：`./patch/setup.sh`。
