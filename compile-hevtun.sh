#!/bin/bash
set -o errexit
set -o pipefail
set -o nounset
# Set magic variables for current file & dir
__dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
__file="${__dir}/$(basename "${BASH_SOURCE[0]}")"
__base="$(basename ${__file} .sh)"
# --- Resolve and validate NDK_HOME (Linux, macOS, Windows Git Bash/MSYS2) ---
# Note: with `set -o nounset`, referencing an unset $NDK_HOME would abort the
# script with an unbound-variable error, so always use the :- default form.
if [[ -z "${NDK_HOME:-}" ]]; then
  echo "Android NDK: NDK_HOME is not set. Please set env \$NDK_HOME to your NDK directory."
  exit 1
fi
# Git Bash users often export NDK_HOME as a Windows path (C:\Users\...\ndk\...).
# Normalize it to a POSIX path when cygpath is available so every later test
# and invocation behaves the same on all platforms.
if command -v cygpath >/dev/null 2>&1; then
  NDK_HOME="$(cygpath -u "$NDK_HOME")"
fi
if [[ ! -d "$NDK_HOME" ]]; then
  echo "Android NDK: NDK_HOME directory not found: $NDK_HOME"
  exit 1
fi

# --- Detect the host platform ---
# Windows hosts (Git Bash / MSYS2 / Cygwin) MUST use the official Windows batch
# entry point ndk-build.cmd. The Unix build/ndk-build script rejects the
# Windows NDK's bundled make.exe with:
#   "ERROR: You are using a non-Cygwin compatible Make program."
# so the Unix script is only used on Linux/macOS.
IS_WINDOWS=0
case "$(uname -s 2>/dev/null || echo unknown)" in
  MINGW*|MSYS*|CYGWIN*) IS_WINDOWS=1 ;;
esac

# --- Detect the correct ndk-build executable for this platform ---
NDK_BUILD=""
NDK_BUILD_CMD=""
if [[ $IS_WINDOWS -eq 1 ]]; then
  if [[ -f "$NDK_HOME/build/ndk-build.cmd" ]]; then
    NDK_BUILD_CMD="$NDK_HOME/build/ndk-build.cmd"
  elif [[ -f "$NDK_HOME/ndk-build.cmd" ]]; then
    NDK_BUILD_CMD="$NDK_HOME/ndk-build.cmd"
  fi
fi
if [[ -z "$NDK_BUILD_CMD" ]]; then
  if [[ -f "$NDK_HOME/build/ndk-build" ]]; then
    NDK_BUILD="$NDK_HOME/build/ndk-build"
  elif [[ -f "$NDK_HOME/ndk-build" ]]; then
    NDK_BUILD="$NDK_HOME/ndk-build"
  elif [[ -f "$NDK_HOME/build/ndk-build.cmd" ]]; then
    NDK_BUILD_CMD="$NDK_HOME/build/ndk-build.cmd"
  elif [[ -f "$NDK_HOME/ndk-build.cmd" ]]; then
    NDK_BUILD_CMD="$NDK_HOME/ndk-build.cmd"
  fi
fi
if [[ -z "$NDK_BUILD" && -z "$NDK_BUILD_CMD" ]]; then
  echo "Android NDK: ndk-build not found under $NDK_HOME (looked in build/ and NDK root)."
  exit 1
fi

# Invoke ndk-build portably.
#
# Unix path: run the shell script through bash so a missing executable bit is
# harmless.
#
# Windows path: arguments such as NDK_LIBS_OUT=/tmp/... are MSYS paths that
# the Windows tools cannot resolve, and values with spaces (APP_CFLAGS,
# APP_ABI) would be mangled by Bash argument passing. Direct `cmd //c` from
# Git Bash is also unreliable in this environment (it can open an interactive
# CMD prompt instead of executing). The reliable launcher is PowerShell:
# it starts the official ndk-build.cmd non-interactively through the Windows
# command interpreter, with a Windows-style path and single-quoted arguments
# (argument values never contain single quotes). ndk-build.cmd sets up its
# own compatible make internally, so no external make is needed on this path.
# MSYS2_ARG_CONV_EXCL disables MSYS path conversion for the whole invocation,
# and the batch file's exit code is propagated back to Bash unchanged.
run_ndk_build() {
  if [[ -n "$NDK_BUILD" ]]; then
    bash "$NDK_BUILD" "$@"
    return
  fi
  local win_ndk_build
  win_ndk_build="$(cygpath -w "$NDK_BUILD_CMD")"
  local ps_command="& '${win_ndk_build}'"
  local arg value
  for arg in "$@"; do
    value="${arg#*=}"
    # Convert any embedded MSYS temp paths to Windows paths.
    if [[ "$value" == "$TMPDIR"* ]]; then
      value="$(cygpath -w "$value")"
      arg="${arg%%=*}=$value"
    fi
    ps_command+=" '${arg}'"
  done
  ps_command+="; exit \$LASTEXITCODE"
  MSYS2_ARG_CONV_EXCL="*" powershell.exe -NoProfile -Command "$ps_command"
}

# --- Ensure GNU make is available (Unix ndk-build path only) ---
# On the Windows .cmd path, ndk-build.cmd wires up its own compatible make;
# adding the NDK's Windows make.exe to PATH for the Unix script is exactly
# what caused the non-Cygwin Make error, so it is never done there.
if [[ -n "$NDK_BUILD" ]] && ! command -v make >/dev/null 2>&1; then
  for _make_dir in "$NDK_HOME"/prebuilt/*/bin; do
    if [[ -f "$_make_dir/make" ]]; then
      PATH="$_make_dir:$PATH"
      break
    fi
  done
fi
if [[ -n "$NDK_BUILD" ]] && ! command -v make >/dev/null 2>&1; then
  echo "GNU make not found. ndk-build requires it."
  echo "  Linux:  install GNU make with your package manager"
  echo "  macOS:  xcode-select --install"
  exit 1
fi
TMPDIR=$(mktemp -d)
clear_tmp () {
  rm -rf $TMPDIR
}
trap 'echo -e "Aborted, error $? in command: $BASH_COMMAND"; trap ERR; clear_tmp; exit 1' ERR INT

ABIS="armeabi-v7a arm64-v8a x86 x86_64"

mkdir -p "$TMPDIR/jni"
pushd "$TMPDIR"

# Stage the HEV sources into the temp build tree.
#
# Linux/macOS: a symlink to the real submodule (upstream behavior, unchanged).
#
# Windows: a plain copy breaks the build twice, so a cleaned staged copy is
# used instead:
#  1. The submodule `.git` gitfile (and every nested submodule `.git`) contains
#     a relative `gitdir:` pointer that is invalid inside the temp tree, which
#     makes git fail with "fatal: gitfile does not point to a valid repository"
#     during the build. All `.git` entries are therefore excluded from the copy.
#  2. Without Developer Mode/admin rights, Git for Windows checks out Git
#     symlinks (mode 120000) as plain text files containing the target path,
#     e.g. include/hev-object-atomic.h literally containing
#     "../src/lib/object/hev-object-atomic.h", which the compiler then rejects.
#     Every path recorded as a symlink in the Git index (main repo and nested
#     submodules) is materialized in the staged copy with the real target
#     file's contents. The upstream checkout itself is never modified.
stage_hev_sources() {
  local dest="jni/hev-socks5-tunnel"
  mkdir -p "$dest"
  cp -r "$__dir/hev-socks5-tunnel/." "$dest/"
  # 1) Drop every .git pointer file/dir from the staged copy.
  find "$dest" -name .git -exec rm -rf {} + 2>/dev/null || true
  # 2) Materialize Git symlinks with their targets' real contents.
  local repo_root rel link_path target resolved tmp_file
  while IFS= read -r repo_root; do
    rel="${repo_root#"$__dir/hev-socks5-tunnel"}"
    rel="${rel#/}"
    while IFS= read -r link_path; do
      local staged_link="$dest${rel:+/$rel}/$link_path"
      [[ -e "$staged_link" || -L "$staged_link" ]] || continue
      # Read the target from the pristine checkout (real symlink or text file).
      if [[ -L "$repo_root/$link_path" ]]; then
        target="$(readlink "$repo_root/$link_path")"
      else
        target="$(tr -d '\r\n' < "$repo_root/$link_path")"
      fi
      # Resolve against the pristine checkout so chained links are followed
      # to the genuine file, then copy that content into the staged tree.
      resolved="$(dirname "$repo_root/$link_path")/$target"
      if [[ -f "$resolved" ]]; then
        tmp_file="$staged_link.materialized"
        cp -fL "$resolved" "$tmp_file"
        rm -f "$staged_link"
        mv "$tmp_file" "$staged_link"
        echo "Materialized symlink: ${rel:+$rel/}$link_path -> $target"
      else
        echo "WARNING: symlink target not found for ${rel:+$rel/}$link_path -> $target" >&2
      fi
    done < <(git -C "$repo_root" ls-files -s 2>/dev/null | awk '$1 == "120000" { $1=""; $2=""; $3=""; sub(/^ +/, ""); print }')
  done < <(find "$__dir/hev-socks5-tunnel" -name .git -exec dirname {} \; | sort -u)
}

if [[ $IS_WINDOWS -eq 1 ]]; then
  stage_hev_sources
elif ! ln -s "$__dir/hev-socks5-tunnel" jni/hev-socks5-tunnel 2>/dev/null; then
  # Non-Windows fallback (e.g. symlink creation refused): same cleaned copy.
  stage_hev_sources
fi

# 1) JNI shared library (libhev-socks5-tunnel.so) — loaded in-process by
#    com.v2ray.ang.service.TProxyService for the VpnService hev tun mode.
echo 'include $(call all-subdir-makefiles)' > jni/Android.mk

run_ndk_build \
    NDK_PROJECT_PATH=. \
    APP_BUILD_SCRIPT=jni/Android.mk \
    "APP_ABI=$ABIS" \
    APP_PLATFORM=android-24 \
    NDK_LIBS_OUT="$TMPDIR/libs" \
    NDK_OUT="$TMPDIR/obj" \
    "APP_CFLAGS=-O3 -DPKGNAME=com/v2ray/ang/service" \
    "APP_LDFLAGS=-Wl,--build-id=none -Wl,--hash-style=gnu" \

# 2) Standalone executable (libhevsockstun.so) — run as a separate root
#    process by com.v2ray.ang.core.root for the Root run mode. Same hev source,
#    no -DENABLE_LIBRARY so hev-main.c's main() is built, and BUILD_EXECUTABLE
#    instead of a shared library. It creates its own tun and reads a YAML config.
cat > jni/exec.mk <<'EXECMK'
TOP_PATH := $(call my-dir)/hev-socks5-tunnel

ifeq ($(filter $(modules-get-list),yaml),)
    include $(TOP_PATH)/third-part/yaml/Android.mk
endif
ifeq ($(filter $(modules-get-list),lwip),)
    include $(TOP_PATH)/third-part/lwip/Android.mk
endif
ifeq ($(filter $(modules-get-list),hev-task-system),)
    include $(TOP_PATH)/third-part/hev-task-system/Android.mk
endif

LOCAL_PATH := $(TOP_PATH)
SRCDIR := $(LOCAL_PATH)/src

include $(CLEAR_VARS)
include $(LOCAL_PATH)/build.mk
LOCAL_MODULE    := hevsockstun
LOCAL_SRC_FILES := $(patsubst $(SRCDIR)/%,src/%,$(SRCFILES))
LOCAL_C_INCLUDES := \
	$(LOCAL_PATH)/src \
	$(LOCAL_PATH)/src/misc \
	$(LOCAL_PATH)/src/core/include \
	$(LOCAL_PATH)/third-part/yaml/include \
	$(LOCAL_PATH)/third-part/lwip/src/include \
	$(LOCAL_PATH)/third-part/lwip/src/ports/include \
	$(LOCAL_PATH)/third-part/hev-task-system/include
LOCAL_CFLAGS += -DFD_SET_DEFINED -DSOCKLEN_T_DEFINED
LOCAL_CFLAGS += $(VERSION_CFLAGS)
ifeq ($(TARGET_ARCH_ABI),armeabi-v7a)
LOCAL_CFLAGS += -mfpu=neon
endif
LOCAL_STATIC_LIBRARIES := yaml lwip hev-task-system
LOCAL_LDFLAGS += -Wl,-z,max-page-size=16384
LOCAL_LDFLAGS += -Wl,-z,common-page-size=16384
include $(BUILD_EXECUTABLE)
EXECMK

run_ndk_build \
    NDK_PROJECT_PATH=. \
    APP_BUILD_SCRIPT=jni/exec.mk \
    "APP_ABI=$ABIS" \
    APP_PLATFORM=android-24 \
    NDK_LIBS_OUT="$TMPDIR/libs-exec" \
    NDK_OUT="$TMPDIR/obj-exec" \
    "APP_CFLAGS=-O3" \
    "APP_LDFLAGS=-Wl,--build-id=none -Wl,--hash-style=gnu" \

# Stage both artifacts under libs/<abi>/. The executable is renamed to
# lib*.so so the APK installer extracts it into nativeLibraryDir as an
# executable file (filename distinct from the JNI library above).
mkdir -p "$__dir/libs"
cp -r "$TMPDIR/libs/"* "$__dir/libs/"
for abi in $ABIS; do
  cp "$TMPDIR/libs-exec/$abi/hevsockstun" "$__dir/libs/$abi/libhevsockstun.so"
done

# Stage the same artifacts where Gradle actually packages them from
# (V2rayNG/app/libs, see jniLibs.srcDirs("libs") in app/build.gradle.kts).
# CI used to perform this copy as a separate manual step; doing it here makes
# a local `bash compile-hevtun.sh` sufficient to produce a working APK and
# prevents shipping an APK that crashes with UnsatisfiedLinkError because
# libhev-socks5-tunnel.so was never copied into the app module.
if [[ -d "$__dir/V2rayNG/app" ]]; then
  mkdir -p "$__dir/V2rayNG/app/libs"
  cp -r "$__dir/libs/"* "$__dir/V2rayNG/app/libs/"
  echo "Staged native libraries into $__dir/V2rayNG/app/libs"
fi

popd
rm -rf $TMPDIR
