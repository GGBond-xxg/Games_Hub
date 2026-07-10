# MD3ELauncher

MD3ELauncher 是一个面向 Android 的轻量级游戏启动器。它不是单一模拟器，而是一个 **游戏启动器 + 模拟器入口管理器**。

项目的核心目标是：把本地 ROM、部分内置模拟器、外部模拟器 App、安卓游戏 App、收藏和基础设置统一放到一个简洁的横屏启动界面里。

当前版本可以理解为两条路线同时存在：

```text
路线 1：内置模拟器
MD3ELauncher 自己打开游戏，例如当前的内置 GBA。

路线 2：外部 App 模拟器
MD3ELauncher 负责扫描游戏、展示游戏、选择模拟器 App、把 ROM 交给外部模拟器 App 打开。
```

也就是说，本项目不只是内部模拟器项目。它也支持一部分外部 App 模拟器启动方案，后续 PSP、FC、NSE/NS 等平台也会优先按“内置能力 + 外部 App 模拟器适配”的方式继续扩展。

---

## 当前功能状态

| 模块 | 状态 | 说明 |
|---|---:|---|
| 启动器首页 | 可用 | 支持平台分类、游戏列表、收藏、安卓应用入口、横屏 UI。 |
| 安卓应用启动 | 可用 | 可以读取已安装 App，并将指定 App 标记为游戏后放入启动器。 |
| 外部模拟器 App 选择 | 可用 | 每个平台可以选择一个已安装的模拟器 App，用于外部启动 ROM。 |
| GBA 内置模拟器 | 可用 | 基于 LibretroDroid + mGBA libretro core，支持游戏内菜单、存档、快速存档、作弊码。 |
| GBA 外部模拟器 | 可用/兼容中 | 支持选择 My Boy!、Pizza Boy、RetroArch、GBA.emu、John GBA 等同类 App，具体能否直进游戏取决于外部 App 的 Intent 支持。 |
| PSP 外部模拟器 | 可用/兼容中 | 支持选择 PPSSPP / RetroArch / PSP 类模拟器 App，当前已有 ISO/CSO/PBP/CHD 扫描和基础启动适配。 |
| Switch / NSE 外部模拟器 | 架构预留 | 已有平台和扩展名预留，后续只做启动器侧适配，不包含 keys、固件或商业资源。 |
| FC | 目录预留 | 已预留 `emulator/fc/`，后续再补平台枚举、扫描规则和模拟器启动方式。 |
| GBA 作弊码 | 稳定方案 | 开启即时生效；关闭会自动快速存档并重启当前 GBA 游戏。 |

---

## 模拟器接入方式说明

### 1. 内置模拟器

内置模拟器是指：游戏直接在 MD3ELauncher 内部打开，不依赖外部 App。

当前已经实现的内置模拟器：

```text
GBA：LibretroDroid + mGBA libretro core
```

GBA 内置模拟器包含：

- GBA ROM 启动
- 横屏触控按键
- 游戏内菜单
- 普通存档位
- 快速存档
- 作弊码添加、开关、删除
- 关闭作弊码时自动快速存档并重启当前游戏

后续如果 PSP、FC 或其他平台要做内置模拟器，会放到对应目录：

```text
app/src/main/java/com/bond/md3elauncher/emulator/psp/
app/src/main/java/com/bond/md3elauncher/emulator/fc/
app/src/main/java/com/bond/md3elauncher/emulator/nse/
```

### 2. 外部 App 模拟器

外部 App 模拟器是指：MD3ELauncher 不自己运行模拟器核心，而是把 ROM 通过 Android Intent 交给用户手机上已经安装的模拟器 App。

流程大致是：

```text
选择平台文件夹
↓
扫描 ROM
↓
给平台选择一个外部模拟器 App
↓
点击游戏
↓
MD3ELauncher 生成 ACTION_VIEW / ACTION_SEND / 专用 Intent
↓
外部模拟器 App 打开 ROM
```

外部模拟器的好处：

- 不需要项目内置所有模拟器核心。
- PSP、部分 GBA、Switch/NSE 等复杂平台可以先走外部 App。
- 用户可以继续使用自己熟悉的模拟器 App。
- 项目本身更像游戏前端和统一入口。

外部模拟器的限制：

- 不是所有模拟器 App 都允许第三方 App 直接传 ROM 启动。
- 有些模拟器只能打开 App 首页，不能自动进入游戏。
- Android 版本、文件权限、模拟器包名、Intent 参数都会影响兼容性。
- 对于不支持直进游戏的模拟器，MD3ELauncher 会尝试打开模拟器本体，并提示后续需要继续适配专用启动参数。

---

## 当前外部模拟器适配范围

外部模拟器启动逻辑主要在：

```text
app/src/main/java/com/bond/md3elauncher/system/ExternalLauncher.kt
```

当前使用的启动策略包括：

```text
ACTION_VIEW + ROM Uri
ACTION_SEND + ROM Uri
content:// 授权 Uri
部分平台缓存临时 ROM 文件后启动
部分模拟器专用 Intent 参数
失败后回退到只打开模拟器 App
```

### GBA 外部模拟器

GBA 目前有两种选择：

```text
默认：内置 GBA 模拟器
可选：外部 GBA 模拟器 App
```

外部 GBA 模拟器适配方向包括：

- My Boy! 专用启动适配
- Pizza Boy / John GBA / GBA.emu / Nostalgia GBA / mGBA 类 App 的通用启动适配
- RetroArch GBA core 启动适配，例如 mGBA、gpSP、VBA 系列 core

如果外部 GBA 模拟器不支持直接接收 ROM，启动器会回退为打开该模拟器 App。

### PSP 外部模拟器

PSP 当前主要走外部模拟器 App 方案。

已支持扫描的 PSP 文件类型：

```text
.iso
.cso
.pbp
.chd
```

外部 PSP 模拟器适配方向包括：

- PPSSPP 类 App
- RetroArch 的 PPSSPP core
- 其他 PSP 模拟器 App 的通用 ACTION_VIEW / ACTION_SEND 方式

PSP 文件通常比较大，所以当前 PSP 启动默认尽量不复制 ROM 到缓存目录，而是优先传原始 Uri。

### Switch / NSE 外部模拟器

Switch / NSE 当前只做启动器侧架构预留。

已预留的文件类型：

```text
.nsp
.xci
.nsz
.nro
```

项目不会提供，也不会内置：

```text
keys
firmware
BIOS
商业游戏资源
```

后续如果做 Switch / NSE，也只做：

```text
扫描本地游戏文件
选择外部模拟器 App
尝试通过外部 App 启动
```

### FC

FC 当前只有目录预留，还没有正式接入平台枚举和扫描逻辑。

预留目录：

```text
app/src/main/java/com/bond/md3elauncher/emulator/fc/
```

后续 FC 可以走两种方向：

```text
方案 A：外部 FC 模拟器 App
方案 B：内置 Libretro FC core
```

---

## GBA 作弊码实现说明

当前 GBA 作弊码采用稳定方案。

### 开启作弊码

在游戏内打开作弊码后，会直接下发到 GBA core。常见 GameShark / CodeBreaker 代码可以即时生效。例如穿墙码开启后，不需要重启游戏。

### 关闭作弊码

关闭或删除已启用的作弊码时，应用会执行下面流程：

```text
自动快速存档
↓
关闭作弊码状态
↓
重启当前 GBA 游戏
↓
恢复开启作弊前记录的 clean state
```

也就是说，当前版本的“关闭作弊码”本质上等同于自动执行：

```text
快速存档 → 退出当前 GBA 游戏 → 重新进入当前 GBA 游戏
```

这样做的原因是：部分 GBA 作弊码，尤其是穿墙类 GameShark / CodeBreaker 代码，会在 mGBA 当前运行环境里留下 patch。单纯调用 `resetCheat()`、软重置、重新下发空作弊码，都无法稳定清除这类效果。

因此当前正式稳定方案是：**关闭作弊码时自动快速存档并重启当前 GBA 游戏**。这个方案不是 My Boy! 那种完全无感关闭，但稳定性更好，不需要用户手动退出再进入。

后续如果继续实现 native CheatManager，可以再尝试做到：

```text
开启作弊码：即时生效
关闭作弊码：直接恢复 ROM/RAM patch，不重启游戏
```

如果 native CheatManager 成本过高或效果不稳定，当前方案可以作为 GBA 作弊码正式方案保留。

---

## 代码目录说明

### 模拟器相关目录

从当前版本开始，模拟器相关代码统一放在：

```text
app/src/main/java/com/bond/md3elauncher/emulator/
```

当前结构：

```text
emulator/
├── InternalEmulators.kt        # 内置模拟器注册入口，目前主要是内置 GBA
├── README.md                   # 模拟器目录说明
├── gba/
│   ├── InternalGbaActivity.kt  # GBA 内置模拟器页面、菜单、输入、存档、作弊码
│   └── GbaNativeCheatBridge.kt # GBA native CheatManager 后续预留入口
├── psp/
│   └── PspIsoReader.kt         # PSP ISO 元数据读取相关代码
├── fc/
│   └── README.md               # FC 模拟器预留目录
└── nse/
    └── README.md               # NSE/NS 模拟器预留目录
```

### 外部模拟器启动目录

外部模拟器 App 启动逻辑放在：

```text
app/src/main/java/com/bond/md3elauncher/system/ExternalLauncher.kt
```

已安装 App 读取和模拟器 App 识别逻辑在：

```text
app/src/main/java/com/bond/md3elauncher/system/AndroidAppRepository.kt
```

ROM 扫描逻辑在：

```text
app/src/main/java/com/bond/md3elauncher/io/RomScanner.kt
```

平台、游戏、已安装 App 等基础模型在：

```text
app/src/main/java/com/bond/md3elauncher/data/Models.kt
```

---

## 后续代码规划

以后新增模拟器，按下面方式区分：

```text
GBA 内置模拟器相关代码      → emulator/gba/
PSP 元数据 / 内置能力预研    → emulator/psp/
FC 元数据 / 内置能力预研     → emulator/fc/
NSE/NS 元数据 / 启动预留     → emulator/nse/
内置模拟器注册入口          → emulator/InternalEmulators.kt
外部模拟器 App 启动适配     → system/ExternalLauncher.kt
已安装 App / 模拟器识别     → system/AndroidAppRepository.kt
ROM 扫描                    → io/RomScanner.kt
```

这样做的目的：

- 内置模拟器和外部模拟器启动逻辑分开。
- 不同平台代码分开，后续 PSP、FC、NSE/NS 更容易维护。
- 学习时可以按平台看代码，不用在 MainActivity 里找所有逻辑。
- 如果某个平台出问题，可以快速定位到对应文件夹。

---

## 技术栈

| 类型 | 技术 |
|---|---|
| 语言 | Kotlin |
| UI | Jetpack Compose / Material 3 |
| Android | compileSdk 36，minSdk 23，targetSdk 36 |
| GBA 内置 Core | LibretroDroid + mGBA libretro core |
| 外部模拟器启动 | Android Intent / content Uri / FileProvider |
| 构建 | Gradle / Android Gradle Plugin |

当前项目依赖 JitPack，因为 LibretroDroid 来自 GitHub 依赖源。`settings.gradle.kts` 中需要保留：

```kotlin
maven("https://jitpack.io")
```

---

## 构建方式

如果项目里已经生成了 Gradle Wrapper：

```bash
./gradlew clean assembleDebug
```

Windows：

```powershell
.\gradlew.bat clean assembleDebug
```

如果没有 Gradle Wrapper，但本机已经安装 Gradle：

```bash
gradle clean assembleDebug
```

安装到设备：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

或：

```bash
gradle installDebug
```

---

## 调试日志

GBA 内置模拟器相关日志：

```bash
adb logcat -d -v time | grep -i "MD3E_GBA\|InternalGbaActivity\|Libretro\|AndroidRuntime\|FATAL\|ANR"
```

Windows PowerShell：

```powershell
adb logcat -d -v time | findstr /i "MD3E_GBA InternalGbaActivity Libretro AndroidRuntime FATAL ANR"
```

外部模拟器启动相关日志可以重点看：

```text
ExternalLauncher
ActivityNotFoundException
Permission Denial
FileProvider
ACTION_VIEW
ACTION_SEND
```

清空日志后重新测试：

```bash
adb logcat -c
```

---

## ROM、BIOS、固件与版权说明

本项目不提供任何商业游戏 ROM、BIOS、密钥、固件或受版权保护的游戏资源。

请只使用你自己合法拥有并自行备份的游戏文件。不同地区对 ROM、BIOS、密钥、固件和模拟器使用的规定不同，上传公开仓库前请自行确认当地法律与相关开源许可证要求。

尤其是 Switch / NSE 相关内容，本项目只做启动器侧架构预留，不会提供：

```text
keys
firmware
prod.keys
title.keys
商业游戏资源
```

如果后续公开发布 APK，建议补充：

```text
LICENSE
THIRD_PARTY_NOTICES.md
```

用于说明项目自身许可证以及第三方依赖许可证。

---

## 后续计划

当前 GBA 作弊码已经作为稳定断点保留。后续优先进入 PSP 模块，而不是继续在 GBA 作弊码上绕方案。

计划顺序：

1. 完善 PSP 游戏扫描和 ISO 信息识别。
2. 优化 PSP 外部模拟器 App 启动适配。
3. 整理 PSP 平台详情页和封面识别。
4. 补充 FC 平台枚举、扫描规则和外部模拟器启动方案。
5. NSE/NS 只保留启动器侧目录和架构预留，不包含任何密钥、固件或商业资源。
6. 如果以后有时间，再回头尝试 GBA native CheatManager。

---

## 当前版本断点

当前建议将这个版本作为 GBA 作弊码稳定断点版：

```text
GBA 开启作弊码：即时生效
GBA 关闭作弊码：自动快速存档 + 自动重启当前 GBA 游戏
GBA 运行方式：支持内置 GBA，也支持部分外部 GBA 模拟器 App
PSP 运行方式：主要支持外部 PSP 模拟器 App
模拟器代码：已整理到 emulator/ 分平台目录
外部模拟器启动：统一在 system/ExternalLauncher.kt
```

如果后续 native CheatManager 无法稳定实现，就以当前方案作为 GBA 金手指正式方案，继续开发 PSP / FC / NSE 等后续模块。
