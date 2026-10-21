# Sidebar for HyperOS / MIUI

A third-party, **root-free** side panel for Xiaomi tablets running HyperOS / MIUI — inspired by MIUI's native sidebar, but with one addition: apps open in a **real freeform (floating window)** via [Shizuku](https://shizuku.rikka.app/).

> Chinese README: [README.zh-CN.md](README.zh-CN.md)

![demo](docs/demo.gif)

---

## Features

- **Edge handle** pinned to the right edge: swipe left or tap to open
- **Drag-follow gesture** — the panel tracks your finger and springs back if you release too early
- **App picker** — choose exactly which apps appear; the panel is a narrow icon-only strip (MIUI-native look)
- **Freeform launch via Shizuku** — apps open in a floating window; if Shizuku is unavailable it falls back to a normal full-screen launch automatically
- **Foreground service + auto-start on boot** (needs the MIUI/HyperOS *Autostart* permission)
- **Quick Settings tile** — start/stop the sidebar without opening the app
- **Self-healing recovery** — opening the app re-starts the service automatically
- **No notification clutter** — the mandatory foreground-service notification is posted on a hidden channel; no entry in Recents
- Liquid-glass panel drawn entirely in code (chamfered/lensed edges, specular highlight — no system blur), plus an Arknights-inspired settings UI

## Requirements

| | |
|---|---|
| OS | Android 13+ — developed and verified on **Xiaomi Pad 8, HyperOS 3.0 / Android 16** |
| Permission | `SYSTEM_ALERT_WINDOW` (overlay) |
| Freeform | [Shizuku](https://shizuku.rikka.app/) (optional — without it apps launch fullscreen) |
| Root | **Not required** |

## Install & setup

1. Build the APK (see [Build](#build)) or grab one from Releases
2. `adb install app-debug.apk`, or copy it to the device and tap it
3. Open the app and, in order:
   - **Grant overlay permission**
   - **Start the service**
   - *(optional)* Install + activate Shizuku, then tap **Authorize Shizuku** in the app
   - *(MIUI/HyperOS)* Settings → Apps → Sidebar → **Autostart** → **on**
     Without this, the system silently drops `BOOT_COMPLETED` and auto-start will not work.
4. Swipe left from the right edge, or tap the handle

### Activating Shizuku without a PC

Shizuku → *Start via wireless debugging* → follow the on-device wizard.
Shizuku (and therefore freeform launch) must be re-activated **after every reboot** — this is inherent to the no-root approach. The sidebar itself keeps working; only the freeform launch falls back to fullscreen until you re-activate.

## How freeform launch works

The central question of this project was: *can a normal third-party app launch **another** app into a freeform window without root?*

Measured on HyperOS 3.0 / Android 16 (Xiaomi Pad 8):

| Approach | Result |
|---|---|
| `FEATURE_FREEFORM_WINDOW_MANAGEMENT` | ✅ present (the device supports freeform) |
| `ActivityOptions.setLaunchBounds()` (public API) | ❌ sets bounds only — cannot select windowing mode |
| `ActivityOptions.setLaunchWindowingMode()` | ❌ hidden API, blocked for third-party apps |
| HyperOS's own "mini window" entry point | ❌ not reachable by third-party apps |
| **`am start --windowingMode 5` executed as shell** | ✅ **works — task reports `mode=freeform`** |
| Specifying bounds | ❌ neither `am` nor `cmd` accepts bounds on this build; the window uses HyperOS's default size/position |

**Conclusion:** a normal app cannot do it; the **shell identity provided by Shizuku can**. So the app binds a Shizuku `UserService` (running as shell) and executes:

```
am start --windowingMode 5 -n <package>/<activity>
```

If anything fails (Shizuku not installed/inactive/not authorized, binder lost), the app re-binds and falls back to a normal launch — so the sidebar is never useless.

## Known limitations

- **MIUI's "Clear all" (`一键清理` / OneKeyClean) force-stops the app**, and the sidebar disappears with it. A force-stop is the OS's highest-privilege termination: it kills every process, cancels alarms and marks the package as *stopped*, so an app cannot block it or restart itself. Recovery paths built into this app:
  - **Open the app** — it re-starts the service by itself
  - **Quick Settings tile** — pull down the shade and tap *Sidebar* (add the tile once via the tile editor; MIUI ignores programmatic tile registration)
  - If you'd rather be protected from the cleaner, re-enable one of the two things this app hides by design: keep the foreground-service notification, or keep the app in Recents so you can **lock** its task (MIUI does not kill locked tasks). Both are deliberate trade-offs — the default build stays quiet instead.
- **System gesture area**: the rightmost ~20 dp of the screen belongs to HyperOS's back-gesture region, which sits above overlay windows. A swipe that *starts* at the very edge may be consumed by the system while an app is in the foreground. Start slightly inside the handle. (On the launcher there is no such conflict.)
- **Shizuku re-activation** after each reboot (see above).
- The system posts its own *"displaying over other apps"* notification for any app holding the overlay permission. It is posted by the system process and can only be disabled in the system notification settings.
- Freeform window bounds are not controllable on this build (see table above).
- Auto-start additionally needs the MIUI/HyperOS **Autostart** permission; a reboot is required to verify.

## Build

Requirements: **JDK 17**, Android SDK with `platforms;android-35` and `build-tools;35.0.0`.

```bash
./gradlew assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

> ⚠️ **The AIDL step breaks if the project path contains non-ASCII characters** (e.g. a Chinese folder name) — the AIDL compiler fails with `MalformedInputException`. Clone into an ASCII-only path, or create a junction/symlink and build through it:
> ```bat
> mklink /J C:\dev\SidebarApp "D:\some\中文\path\SidebarApp"
> C:\dev\SidebarApp\gradlew.bat assembleDebug --project-dir C:\dev\SidebarApp
> ```

A GitHub Actions workflow ([`.github/workflows/build.yml`](.github/workflows/build.yml)) builds a debug APK and uploads it as an artifact. It runs **only when started manually**: open the repo's **Actions** tab, pick **Build**, and tap **Run workflow**.

### Flat-root layout (upload without creating folders)

This copy of the repo can be uploaded with every file in the repo root, which is handy on a phone where creating folders is awkward. Each file's folder path is encoded in its name, with `__` standing in for `/`, for example:

| Flat file name | Real location |
| --- | --- |
| `app__build.gradle.kts` | `app/build.gradle.kts` |
| `app__src__main__AndroidManifest.xml` | `app/src/main/AndroidManifest.xml` |
| `app__src__main__res__values__strings.xml` | `app/src/main/res/values/strings.xml` |
| `docs__demo.gif` | `docs/demo.gif` |

The script `restructure.sh` turns the flat names back into real folders. The workflow runs it before building, so nothing else is needed on GitHub. To build locally, run `bash restructure.sh` once and then `./gradlew assembleDebug`. If the repo already has the normal folder structure, the script does nothing.
