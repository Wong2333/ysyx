#!/bin/bash
# setup.sh —— 拉取 ysyxSoC / rt-thread-am，并用 make dev-init 初始化子模块后打补丁
#
#   ./patch/setup.sh        # 默认：ysyxSoC -> ./ysyxSoC，rt-thread-am -> ./rt-thread-am（都在 workbench 下）
#   ./patch/setup.sh -f     # 已存在则删除重来（会丢本地改动）
#
#   SOC_DIR=... RTT_DIR=... 可改目标目录
#   SOC_REPO/RTT_REPO、SOC_BRANCH/RTT_BRANCH 可换远端
#
# 补丁基准见 patch/ysyxSoC/BASE、patch/rt-thread-am/BASE；脚本会先 checkout 到基准再打补丁。

set -euo pipefail
cd "$(dirname "$0")/.."
PATCH_DIR=$PWD/patch

SOC_DIR="${SOC_DIR:-$PWD/ysyxSoC}"
RTT_DIR="${RTT_DIR:-$PWD/rt-thread-am}"
SOC_REPO="${SOC_REPO:-git@github.com:OSCPU/ysyxSoC.git}"
SOC_BRANCH="${SOC_BRANCH:-ysyx6}"
RTT_REPO="${RTT_REPO:-git@github.com:NJU-ProjectN/rt-thread-am.git}"
RTT_BRANCH="${RTT_BRANCH:-master}"

FORCE=0
if [ "${1:-}" = "-f" ] || [ "${1:-}" = "--force" ]; then FORCE=1; fi

log() { printf '\033[1;34m[setup]\033[0m %s\n' "$*"; }

# clone <repo> <branch> <dir> <name>；已存在且没 -f 则返回 1
clone() {
    if [ -d "$3" ]; then
        [ "$FORCE" = 1 ] || { log "$4 已存在，跳过：$3（-f 重来）"; return 1; }
        rm -rf "$3"
    fi
    log "clone $4 -> $3"
    git clone -b "$2" "$1" "$3"
}

# apply <patch 子目录名> <仓库目录>
apply() {
    local p
    for p in "$PATCH_DIR/$1"/*.patch; do
        git -C "$2" apply "$p"
        log "  patch $(basename "$p")"
    done
}

# ---------------- ysyxSoC ----------------
if clone "$SOC_REPO" "$SOC_BRANCH" "$SOC_DIR" ysyxSoC; then
    git -C "$SOC_DIR" checkout -B "$SOC_BRANCH" "$(cat "$PATCH_DIR/ysyxSoC/BASE")"
    log "make dev-init（拉 rocket-chip 子模块，比较慢）"
    ( cd "$SOC_DIR" && make dev-init )
    apply ysyxSoC "$SOC_DIR"
fi

# ---------------- rt-thread-am ----------------
if clone "$RTT_REPO" "$RTT_BRANCH" "$RTT_DIR" rt-thread-am; then
    git -C "$RTT_DIR" checkout -B "$RTT_BRANCH" "$(cat "$PATCH_DIR/rt-thread-am/BASE")"
    apply rt-thread-am "$RTT_DIR"
fi

cat <<EOF

完成。环境变量：
  export AM_HOME=$PWD/abstract-machine
  export NEMU_HOME=$PWD/nemu
  export NVBOARD_HOME=$PWD/nvboard
  export YSYX_SoC_HOME=$SOC_DIR
EOF
