# HardVPN — Build & Upstream Alignment Notes

HardVPN is a UI/product layer on top of the official 2dust/v2rayNG engine.
The VPN engine, Android VpnService, TUN handling, packet forwarding, DNS and
traffic statistics are **not** reimplemented; they are the upstream code.

## Upstream alignment audit (2026-11)

Compared against upstream `2dust/v2rayNG` master:

| Area | Result |
|---|---|
| `service/CoreVpnService.kt` | Identical to upstream — TUN creation, HEV tun2socks, lifecycle unchanged |
| `service/TProxyService.kt` | Identical to upstream — JNI `System.loadLibrary("hev-socks5-tunnel")`, `-DPKGNAME=com/v2ray/ang/service` |
| `core/LauncherManager.kt` | Identical to upstream |
| `core/CoreServiceManager.kt` | Upstream code + one additive HardVPN block: monotonic session traffic totals, `MSG_QUERY_TRAFFIC`/`MSG_TRAFFIC_RESULT`, session-start timestamp. No lifecycle or routing behavior changed |
| `AppConfig.kt` | Upstream constants + HardVPN message/cache constants only |
| HardVPN additions | `ui/hardvpn/**` (UI + thin coordinator), launcher activity, app name, native packaging guard |

The HardVPN connection coordinator is only an orchestration layer: it
serializes connect/disconnect requests from Home / ExploreVPN / Manual Import
and then calls the official `LauncherManager`/`CoreVpnService`. There is one
Android VPN connection at a time, owned by the official service.

## Native libraries

Two distinct artifacts are built from the pinned `hev-socks5-tunnel`
submodule by `compile-hevtun.sh`:

- `libhev-socks5-tunnel.so` — JNI library loaded in-process by `TProxyService`
- `libhevsockstun.so` — standalone executable for the upstream root run mode

Both are gitignored build outputs. They are staged into `libs/<abi>/`
(CI cache) and `V2rayNG/app/libs/<abi>/` (what Gradle packages, see
`jniLibs.srcDirs("libs")`). Gradle's `verifyHevJniLibs` task fails APK
packaging if `libhev-socks5-tunnel.so` is missing for a packaged ABI — never
disable it; a missing library crashes Connect at runtime with
`UnsatisfiedLinkError`.

Supported ABIs: `arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`.
Redmi Note 12 (Snapdragon 685 / Dimensity 1080 variants) is **arm64-v8a**.

## Windows (Git Bash / MSYS2 UCRT64) build prerequisites

1. JDK 21 (Temurin) and the Android SDK with NDK `30.0.16248370`.
2. `git submodule update --init` (HEV + AndroidLibXrayLite sources).
3. On Windows the script uses the official `ndk-build.cmd`, which sets up its
   own compatible make internally — do **not** run the Unix `build/ndk-build`
   script with the NDK's Windows `make.exe` (it fails with "You are using a
   non-Cygwin compatible Make program"), and no external make or Cygwin/MSYS2
   installation is required. (GNU make is only needed on the Linux/macOS
   Unix-script path.)
4. Export the NDK path (Windows or POSIX form both work):
   `export NDK_HOME="C:\Users\<you>\AppData\Local\Android\Sdk\ndk\30.0.16248370"`
5. `bash compile-hevtun.sh` — detects `build/ndk-build` (or the `.cmd`
   wrappers), builds all ABIs and stages the outputs automatically.
6. Build the app from `V2rayNG/` (e.g. `./gradlew assembleFdroidDebug`).

Linux/macOS: same steps; `ndk-build` is detected in `build/` as before and
CI (`.github/workflows/build.yml`, ubuntu + NDK 30) is unchanged.

## Verification status

Native and APK builds require a machine with JDK/Android SDK/NDK. Until a
build has actually run and the APK inspected for
`lib/arm64-v8a/libhev-socks5-tunnel.so`, treat connectivity as **not
device-verified**; final confirmation is a real connection on the Redmi
Note 12.
