# Sidebar for HyperOS / MIUI · 小米平板侧边栏

一个**不需要 Root** 的第三方侧边栏，为运行 HyperOS / MIUI 的小米平板设计——操作方式对齐 MIUI 原生侧边栏，但多了一件事：**通过 [Shizuku](https://shizuku.rikka.app/) 让应用以真正的 freeform（小窗）打开**。

> English: [README.md](README.md)

![demo](docs/demo.gif)

---

## 功能

- **贴边把手**：固定在屏幕右缘，向左滑或轻点呼出
- **跟手拖拽**：面板跟着手指移动，松手时拖过 1/3 展开、不到则回弹
- **应用选择**：自己挑哪些应用上栏；面板是窄条纯图标（MIUI 原生观感）
- **Shizuku 小窗启动**：点击图标以小窗打开；Shizuku 不可用时自动回退全屏启动
- **前台服务 + 开机自启**（需 HyperOS 的「自启动」授权）
- **快捷设置磁贴**：不打开 App 也能启停侧边栏
- **自愈恢复**：打开 App 自动把服务拉起来
- **不打扰**：前台服务通知放在隐藏渠道，不显示；也不出现在最近任务里
- 液态玻璃面板（纯代码绘制：切角、边缘折射、镜面高光，不依赖系统模糊）+ 明日方舟风格设置界面

## 环境要求

| | |
|---|---|
| 系统 | Android 13+ —— 在 **Xiaomi Pad 8 / HyperOS 3.0 / Android 16** 上开发与验证 |
| 权限 | `SYSTEM_ALERT_WINDOW`（悬浮窗） |
| 小窗 | [Shizuku](https://shizuku.rikka.app/)（可选；不装则全屏启动） |
| Root | **不需要** |

## 安装与配置

1. 自行编译（见[构建](#构建)）或从 Releases 下载 APK
2. `adb install app-debug.apk`，或拷贝到平板点击安装
3. 打开应用，按顺序操作：
   - **授予悬浮窗权限**
   - **启动服务**
   - *（可选）* 装好并激活 Shizuku 后，在应用里点 **授权 Shizuku**
   - *（MIUI/HyperOS）* 设置 → 应用管理 → 侧边栏 → **自启动 → 打开**
     不开这一项，系统会直接丢弃 `BOOT_COMPLETED`，开机自启失效。
4. 从右侧边缘向左滑，或轻点把手

### 不用电脑激活 Shizuku

打开 Shizuku → **通过无线调试启动** → 按设备上的分步向导操作。
Shizuku（以及小窗功能）**每次重启平板后都需要重新激活**——这是无 Root 方案的固有限制。侧边栏本身照常工作，只是在小窗不可用时回退成全屏启动。

## 小窗是怎么实现的

这个项目的核心疑问是：*普通第三方应用能否在不 Root 的前提下，把**别的**应用拉成小窗？*

在 HyperOS 3.0 / Android 16（Xiaomi Pad 8）实测：

| 方案 | 结果 |
|---|---|
| `FEATURE_FREEFORM_WINDOW_MANAGEMENT` | ✅ 存在（设备支持 freeform） |
| `ActivityOptions.setLaunchBounds()`（公开 API） | ❌ 只能设边界，无法指定 windowing mode |
| `ActivityOptions.setLaunchWindowingMode()` | ❌ 隐藏 API，第三方被限制 |
| HyperOS 自家小窗入口 | ❌ 第三方无法调用 |
| **以 shell 身份执行 `am start --windowingMode 5`** | ✅ **成功，任务报告 `mode=freeform`** |
| 指定小窗 bounds | ❌ 本机 `am` / `cmd` 都不支持该参数，只能用 HyperOS 默认尺寸位置 |

**结论**：普通权限做不到；**Shizuku 提供的 shell 身份可以**。因此应用绑定一个以 shell 身份运行的 Shizuku `UserService`，执行：

```
am start --windowingMode 5 -n <包名>/<Activity>
```

任何一步失败（Shizuku 未安装/未激活/未授权、binder 失联）都会自动回退普通启动，侧边栏不会因此变成废物。

## 已知限制

- **MIUI 的「清除全部」会 force-stop 本应用**，侧边栏随之消失。force-stop 是系统最高权限的终止手段：杀光所有进程、取消所有 alarm、把包标记为 `stopped`——应用既无法阻止，也无法自动复活。本项目内置的恢复路径：
  - **打开 App** —— 它会自动重新启动服务
  - **快捷设置磁贴** —— 下拉通知栏点「侧边栏」（首次需要在磁贴编辑里手动添加；MIUI 不接受程序化注册）
  - 如果你更希望免于被清理，可以按需恢复本项目刻意隐藏的两样东西之一：保留前台服务通知，或让它在最近任务中可见以便**锁定任务**（MIUI 不会清理已锁定的任务）。这两种都是刻意的取舍，默认版本选择"安静"。
- **系统手势区**：屏幕最右侧约 20dp 属于 HyperOS 返回手势区，层级在悬浮窗之上。应用在前台时，从**最边缘**起手的滑动可能被系统吃掉——从把手稍内侧一点起手即可（桌面上没有这个冲突）。
- **每次重启需重新激活 Shizuku**（见上）。
- 系统会为所有持有悬浮窗权限的应用发布一条自己的「正在其他应用上层显示」通知，第三方无法删除，只能在系统通知设置里关掉。
- 小窗尺寸位置在当前系统版本上无法自定义（见上表）。
- 开机自启还需要 MIUI/HyperOS 的「自启动」权限，且需要重启一次才能验证。

## 构建

需要 **JDK 17**，Android SDK 包含 `platforms;android-35` 与 `build-tools;35.0.0`。

```bash
./gradlew assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

> ⚠️ **项目路径含非 ASCII 字符（比如中文目录）会导致 AIDL 编译崩溃**（`MalformedInputException`）。请克隆到纯英文路径，或建目录联接后用英文路径构建：
> ```bat
> mklink /J C:\dev\SidebarApp "D:\some\中文\path\SidebarApp"
> C:\dev\SidebarApp\gradlew.bat assembleDebug --project-dir C:\dev\SidebarApp
> ```

仓库自带 GitHub Actions 工作流（[`.github/workflows/build.yml`](.github/workflows/build.yml)），每次推送自动构建 debug APK 并上传为产物。

## 开发踩坑记录（HyperOS 专属）

这些都是花了不少时间才定位的问题，记录在此供参考：

1. **「收起闪现」是窗口销毁期的渲染残留**：销毁悬浮窗时 SurfaceFlinger 会以**未应用 view transform 的位置**（完全展开态）回放该窗口 1~2 帧。对录屏做逐帧亮度分析，能在销毁时刻看到恒定的 +11/255 亮度尖峰。解法：**面板窗口永不销毁**，收起改为「停泊」（原尺寸原地 + `FLAG_NOT_TOUCHABLE`）。
2. **悬浮窗上的 `FLAG_BLUR_BEHIND` 会让整机卡死**（HyperOS）。真实背景模糊在此不可行，玻璃质感只能用渐变绘制模拟。
3. **`FLAG_DIM_BEHIND`** 在移除压暗层时会渲染一帧过亮画面，同样是单帧闪源。
4. **HyperOS 独占屏幕右缘**（`GestureStubRight`，约 54px 宽，层级高于悬浮窗）作为返回手势区。
5. **MIUI/HyperOS 下 `NOT_TOUCHABLE` 的悬浮窗可能吞掉触摸**：一个停泊（不可见）的面板窗口会阻止**下层**悬浮窗收到输入。停泊窗口要避开触摸区域，并在停泊时移除 `FLAG_WATCH_OUTSIDE_TOUCH`。
6. **悬浮窗位置会被系统钳制回屏内**：`Gravity.END` 配负 `x`、`Gravity.START` 配屏幕外坐标，都会被钳回可视区域——窗口无法停到屏幕外。
7. **隐藏 API 不要碰**：别用反射调 `setLaunchWindowingMode`，shell + `am start` 才是可行路径。
8. **Android 11+ 包可见性**：不加 `<queries>` 声明 `MAIN`/`LAUNCHER`，`queryIntentActivities` 只能拿到寥寥几个系统应用。
9. **Shizuku `UserServiceArgs` 必须设置 `processNameSuffix()`**（缺失会抛 `NullPointerException: process name suffix must not be null`）。另外：**不解绑就会泄漏进程**——每个服务生命周期残留一个 `:shell` 进程（约 100MB RSS），务必在 `onDestroy` 里 unbind；而 **force-stop 会完全跳过 `onDestroy`**（MIUI 的「清除全部」正是如此），所以还需要给该服务加一个空闲自退看门狗。
10. **开机自启**需要 `RECEIVE_BOOT_COMPLETED` **加上** MIUI 的「自启动」授权；MIUI 还会把广播延迟约 10~20 秒投递。

## 许可证

[Apache-2.0](LICENSE)

界面风格致敬《明日方舟》的设计语言；仓库内不含任何游戏素材，本项目与 HyperOS/MIUI、小米、鹰角网络均无关联。
