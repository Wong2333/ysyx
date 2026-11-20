#!/bin/bash
# setup.sh —— 拉取 ysyxSoC / rt-thread-am 并自动应用 patch/ 下的本地补丁
#
# 用法：
#   ./patch/setup.sh                    # ysyxSoC -> <workbench>/ysyxSoC，RTT -> ~/temp/rt-thread-am
#   ./patch/setup.sh -f                 # 目标已存在时删除后重来（会丢本地改动，慎用）
#   ./patch/setup.sh --soc-dir DIR --rtt-dir DIR
#   ./patch/setup.sh --skip-soc         # 只处理 RTT
#   ./patch/setup.sh --skip-rtt         # 只处理 ysyxSoC
#   ./patch/setup.sh --no-submodule     # clone 后不初始化 rocket-chip 子模块
#   ./patch/setup.sh --dry-run          # 只检查补丁与远端基准是否一致，不 clone / 不改文件
#
# 远端可用环境变量覆盖（默认 ssh；不通时换成 https）：
#   SOC_REPO=... SOC_BRANCH=ysyx6   RTT_REPO=... RTT_BRANCH=master
#
# 补丁（每个文件一个 patch，基准是对应远端分支）：
#   patch/ysyxSoC/*.patch        -> ysyxSoC origin/ysyx6
#   patch/rt-thread-am/*.patch   -> rt-thread-am origin/master
#
# 说明：
# - 只在“新 clone”时打补丁；目标已存在则跳过（避免在已打过的树上重复插入），需要重来加 -f；
# - ysyxSoC 的 `make verilog` 会自己下载 firtool，不在本脚本范围内。

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
WORKSPACE_DIR=$(cd "$SCRIPT_DIR/.." && pwd)
PATCH_ROOT="$SCRIPT_DIR"

SOC_REPO="${SOC_REPO:-git@github.com:OSCPU/ysyxSoC.git}"
SOC_BRANCH="${SOC_BRANCH:-ysyx6}"
SOC_DIR="${SOC_DIR:-$WORKSPACE_DIR/ysyxSoC}"

RTT_REPO="${RTT_REPO:-git@github.com:NJU-ProjectN/rt-thread-am.git}"
RTT_BRANCH="${RTT_BRANCH:-master}"
RTT_DIR="${RTT_DIR:-$HOME/temp/rt-thread-am}"

FORCE=0
SKIP_SOC=0
SKIP_RTT=0
DRY_RUN=0
SUBMODULE=1

log()  { printf '\033[1;34m[setup]\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m[warn ]\033[0m %s\n' "$*"; }
die()  { printf '\033[1;31m[error]\033[0m %s\n' "$*" >&2; exit 1; }

usage() { sed -n '3,17p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; }

while [ $# -gt 0 ]; do
    case "$1" in
        -f|--force)      FORCE=1 ;;
        --skip-soc)      SKIP_SOC=1 ;;
        --skip-rtt)      SKIP_RTT=1 ;;
        --no-submodule)  SUBMODULE=0 ;;
        --dry-run)       DRY_RUN=1 ;;
        --soc-dir)       SOC_DIR="${2:?--soc-dir 需要目录}"; shift ;;
        --rtt-dir)       RTT_DIR="${2:?--rtt-dir 需要目录}"; shift ;;
        --soc-branch)    SOC_BRANCH="${2:?--soc-branch 需要分支名}"; shift ;;
        --rtt-branch)    RTT_BRANCH="${2:?--rtt-branch 需要分支名}"; shift ;;
        -h|--help)       usage; exit 0 ;;
        *)               die "未知参数: $1（--help 查看用法）" ;;
    esac
    shift
done

# 拉取仓库；返回 0 = 本次新 clone，1 = 已存在跳过
fetch_repo() {
    local name=$1 repo=$2 branch=$3 dir=$4
    if [ -e "$dir" ] && [ ! -d "$dir/.git" ]; then
        die "$dir 已存在但不是 git 仓库，请先移走或换个目录"
    fi
    if [ -d "$dir/.git" ]; then
        if [ "$FORCE" = 0 ]; then
            log "$name 已存在，跳过 clone：$dir（重来加 -f）"
            return 1
        fi
        log "删除旧目录 $dir（--force）"
        [ "$DRY_RUN" = 1 ] || rm -rf "$dir"
    fi
    log "clone $name [$branch] -> $dir"
    if [ "$DRY_RUN" = 1 ]; then
        log "[dry-run] git clone --branch $branch $repo $dir"
        return 1
    fi
    mkdir -p "$(dirname "$dir")"
    git clone --branch "$branch" "$repo" "$dir" \
        || die "$name clone 失败（ssh 不通可设 SOC_REPO/RTT_REPO 为 https 地址）"
    return 0
}

# 在临时 index 上校验补丁能否干净地打到远端基准（不动工作区）
check_against_base() {
    local dir=$1 ref=$2 patch=$3
    local idx
    idx=$(mktemp)
    if ! GIT_INDEX_FILE="$idx" git -C "$dir" read-tree "$ref" 2>/dev/null; then
        rm -f "$idx"; die "读取基准 $ref 失败（没有该分支？）"
    fi
    if GIT_INDEX_FILE="$idx" git -C "$dir" apply --cached --check "$patch" 2>/dev/null; then
        log "  ok      $(basename "$patch")"
        rm -f "$idx"
    else
        rm -f "$idx"
        die "补丁与基准 $ref 不一致：$patch"
    fi
}

# 读取补丁基准 commit（patch/<name>/BASE）；没有则回退到 origin/<branch>
base_of() {
    local name=$1
    if [ -f "$PATCH_ROOT/$name/BASE" ]; then
        tr -d '[:space:]' < "$PATCH_ROOT/$name/BASE"
    fi
}

apply_all() {
    local name=$1 dir=$2 ref=$3
    local -a patches=("$PATCH_ROOT/$name"/*.patch)
    if [ ! -e "${patches[0]}" ]; then
        warn "没有补丁：$PATCH_ROOT/$name/*.patch"
        return 0
    fi
    log "打补丁：$name（${#patches[@]} 个，基准 $ref）"
    local p
    for p in "${patches[@]}"; do
        if [ "$DRY_RUN" = 1 ]; then
            check_against_base "$dir" "$ref" "$p"
        else
            git -C "$dir" apply "$p" || die "应用失败：$p"
            log "  apply   $(basename "$p")"
        fi
    done
}

# ---------------- ysyxSoC ----------------
if [ "$SKIP_SOC" = 0 ]; then
    log "== ysyxSoC =="
    if fetch_repo "soc" "$SOC_REPO" "$SOC_BRANCH" "$SOC_DIR"; then
        base=$(base_of ysyxSoC)
        if [ -n "$base" ]; then
            log "checkout 补丁基准 $base"
            git -C "$SOC_DIR" checkout --detach "$base" >/dev/null 2>&1 \
                || die "checkout $base 失败（基准备份不存在？）"
        fi
        if [ "$SUBMODULE" = 1 ]; then
            log "初始化子模块（rocket-chip），比较慢"
            ( cd "$SOC_DIR" && git submodule update --init --recursive ) \
                || die "子模块初始化失败（--no-submodule 可跳过）"
        else
            warn "跳过子模块（--no-submodule）；make verilog 前需自行 make dev-init"
        fi
        apply_all "ysyxSoC" "$SOC_DIR" "${base:-origin/$SOC_BRANCH}"
    elif [ "$DRY_RUN" = 1 ]; then
        base=$(base_of ysyxSoC)
        apply_all "ysyxSoC" "$SOC_DIR" "${base:-origin/$SOC_BRANCH}"
    fi
fi

# ---------------- rt-thread-am ----------------
if [ "$SKIP_RTT" = 0 ]; then
    log "== rt-thread-am =="
    if fetch_repo "rtt" "$RTT_REPO" "$RTT_BRANCH" "$RTT_DIR"; then
        base=$(base_of rt-thread-am)
        if [ -n "$base" ]; then
            log "checkout 补丁基准 $base"
            git -C "$RTT_DIR" checkout --detach "$base" >/dev/null 2>&1 \
                || die "checkout $base 失败（基准备份不存在？）"
        fi
        apply_all "rt-thread-am" "$RTT_DIR" "${base:-origin/$RTT_BRANCH}"
    elif [ "$DRY_RUN" = 1 ]; then
        base=$(base_of rt-thread-am)
        apply_all "rt-thread-am" "$RTT_DIR" "${base:-origin/$RTT_BRANCH}"
    fi
fi

cat <<EOF

完成。构建前确认环境变量（已配好可忽略）：
  export AM_HOME=$WORKSPACE_DIR/abstract-machine
  export NEMU_HOME=$WORKSPACE_DIR/nemu
  export NVBOARD_HOME=$WORKSPACE_DIR/nvboard
  export YSYX_SoC_HOME=$SOC_DIR
  export NPC_HOME=$WORKSPACE_DIR/npc

RTT：cd $RTT_DIR/bsp/abstract-machine && make ARCH=riscv32e-ysyxsoc
SoC：cd $SOC_DIR && make verilog   # 会自动下载 firtool
EOF
