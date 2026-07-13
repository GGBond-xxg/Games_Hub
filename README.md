# MD3ELauncher

MD3ELauncher 是一个面向 Android 的轻量级游戏启动器。它不是单一模拟器，而是一个 **游戏启动器 + 模拟器入口管理器**。

项目的核心目标是：把本地 ROM、部分内置模拟器、外部模拟器 App、安卓游戏 App、收藏和基础设置统一放到一个简洁的横屏启动界面里。

当前版本可以理解为两条路线同时存在：

```text
路线 1：内置模拟器
MD3ELauncher 自己打开游戏，例如当前的内置 GBA、内置 FC/NES。

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
| GB/GBC 内置模拟器 | 可用 | 复用 mGBA libretro core，新增 GB/GBC 独立平台，支持 `.gb/.gbc/.sgb` 和普通 `.zip` 内 ROM。 |
| GBA 外部模拟器 | 可用/兼容中 | 支持选择 My Boy!、Pizza Boy、RetroArch、GBA.emu、John GBA 等同类 App，具体能否直进游戏取决于外部 App 的 Intent 支持。 |
| PSP 外部模拟器 | 可用/兼容中 | 支持选择 PPSSPP / RetroArch / PSP 类模拟器 App，当前已有 ISO/CSO/PBP/CHD 扫描和基础启动适配。 |
| Switch / NSE 外部模拟器 | 架构预留 | 已有平台和扩展名预留，后续只做启动器侧适配，不包含 keys、固件或商业资源。 |
| FC/NES 内置模拟器 | Phase 1 | 基于 LibretroDroid + Nestopia libretro core，支持 `.nes` 和普通 `.zip` 内 ROM，带虚拟按键、菜单、快速存档/读档。 |
| FC/NES 外部模拟器 | 可用/兼容中 | 支持 `.nes/.fds/.unf/.unif/.zip/.7z` 扫描，推荐选择 Nes.emu、Nostalgia.NES、RetroArch 等外部模拟器启动。 |
| GBA 作弊码 | 稳定方案 | 开启即时生效；支持多个自定义作弊码分 slot 下发；关闭会自动快速存档、重启当前 GBA 游戏，并在重启后自动快读。 |

---

## 模拟器接入方式说明

### 1. 内置模拟器

内置模拟器是指：游戏直接在 MD3ELauncher 内部打开，不依赖外部 App。

当前已经实现的内置模拟器：

```text
GBA：LibretroDroid + mGBA libretro core
GB/GBC：LibretroDroid + mGBA libretro core
FC/NES：LibretroDroid + Nestopia libretro core
```

GBA 内置模拟器包含：

- GBA ROM 启动
- 横屏触控按键
- 游戏内菜单
- 普通存档位
- 快速存档
- 作弊码添加、开关、删除
- 关闭作弊码时自动快速存档、重启当前游戏，并在重启后自动快读

后续如果 PSP 或其他平台要做内置模拟器，会放到对应目录：

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



### GB/GBC 当前实现方式

GB/GBC 从 v0.1.76 起作为独立平台接入。底层先复用当前稳定的 mGBA libretro core，避免为 `.gb/.gbc` 文件误放到 FC/NES。

当前支持：

```text
平台标签：GB
平台名称：GB/GBC
ROM 扩展名：.gb / .gbc / .sgb / .zip / .7z
内置核心：mGBA libretro core
推荐外部模拟器：My OldBoy! / Pizza Boy C / RetroArch / GBC.emu
```

当前规则：

```text
GBA 文件继续放 GBA 平台
GB / GBC 文件放 GB/GBC 平台
FC/NES 文件放 FC/NES 平台
```

内置 GB/GBC 启动说明：

- `.gb / .gbc / .sgb` 可直接读取。
- 普通 `.zip` 会尝试解出里面的 `.gb / .gbc / .sgb / .gba / .agb / .bin`。
- `.7z` 目前只做扫描，内置模拟器暂不直接解压；需要先解压，或使用外部模拟器。
- GB/GBC 与 GBA 共用内置模拟器公共 UI、手柄快捷键、虚拟按键布局和存档菜单规范。

### FC/NES 当前实现方式

FC/NES 从 v0.1.57 起先按外部模拟器方案接入；v0.1.60 起从推荐列表移除 John NESS，优先推荐 Nes.emu / Nostalgia.NES / RetroArch；v0.1.64 起加入内置 FC/NES Phase 1，v0.1.73 起固定使用 Nestopia libretro core；v0.1.65 起增加内置模拟器通用手柄快捷键设置，并修正 FC/NES 中文/特殊字符 ROM 路径兼容。

当前支持：

```text
平台标签：FC
平台名称：FC/NES
ROM 扩展名：.nes / .fds / .unf / .unif / .zip / .7z
内置核心：Nestopia libretro core
推荐外部模拟器：Nes.emu / RetroArch / Nostalgia.NES
RetroArch core 候选：FCEUmm / Nestopia / QuickNES / Mesen（仅外部 RetroArch 使用）
```

当前流程：

```text
选择 FC/NES ROM 文件夹
↓
扫描 ROM
↓
默认可直接使用内置 FC/NES 模拟器
↓
也可以绑定 Nes.emu / RetroArch 等外部模拟器 App
↓
点击游戏启动
```

内置 FC/NES Phase 1 说明：

- `.nes` 可直接读取。
- 普通 `.zip` 会尝试解出里面的 `.nes / .fds / .unf / .unif`。
- `.7z` 暂不在内置模拟器里直接解压；需要先解压成 `.nes`，或者继续使用 Nes.emu / RetroArch 外部模拟器。
- `.fds` 可能需要 FDS BIOS，当前 Phase 1 只做 core 接入，不额外内置 BIOS。

### v0.1.58 补充

- 编辑显示信息页改为横向紧凑标题栏：返回、标题、当前游戏名省略显示、取消、保存。
- 显示名称不再放在默认输入框里，改为点击“编辑显示名称”后展开输入框，避免横屏界面拥挤，并修复名称无法编辑的问题。
- FC/NES 外部启动增加 John NESS 专用候选 Activity / extras，以及 Nes.emu 的 `com.imagine.BaseActivity` 启动候选。
- 注意：John NESS 部分版本只公开 ROM 列表页，不一定允许第三方启动器直接传 ROM 进入游戏；v0.1.60 起不再把 John NESS 放入 FC/NES 推荐列表，建议优先用 Nes.emu 或 RetroArch 做一键直启。

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
快速存档 → 关闭作弊码状态 → 退出当前 GBA 游戏 → 重新进入当前 GBA 游戏 → 自动快速读档
```

这样做的原因是：部分 GBA 作弊码，尤其是穿墙类 GameShark / CodeBreaker 代码，会在 mGBA 当前运行环境里留下 patch。单纯调用 `resetCheat()`、软重置、重新下发空作弊码，都无法稳定清除这类效果。

因此当前正式稳定方案是：**关闭作弊码时自动快速存档、重启当前 GBA 游戏，并在重启后自动快速读档**。这个方案不是 My Boy! 那种完全无感关闭，但稳定性更好，不需要用户手动退出再进入，也尽量保留关闭前的最新进度。

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
├── InternalEmulators.kt          # 内置模拟器注册入口，目前主要是内置 GBA
├── README.md                     # 模拟器目录说明
├── gba/
│   ├── README.md                 # GBA 内置模拟器代码说明
│   ├── InternalGbaActivity.kt    # GBA Activity 生命周期、ROM 启动、存档、作弊码流程
│   ├── GbaTouchControlsView.kt   # GBA 虚拟按键、菜单绘制、作弊菜单 UI
│   ├── GbaModels.kt              # GBA 菜单枚举、按键模型、存档模型、作弊码模型
│   └── GbaNativeCheatBridge.kt   # GBA native CheatManager 后续预留入口
├── psp/
│   └── PspIsoReader.kt           # PSP ISO 元数据读取相关代码
├── fc/
│   └── README.md                 # FC 模拟器预留目录
└── nse/
    └── README.md                 # NSE/NS 模拟器预留目录
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

GBA 目前又进一步拆分了文件：

```text
InternalGbaActivity.kt   负责 Activity 生命周期、ROM 启动、存档/读档、作弊码开关流程
GbaTouchControlsView.kt  负责屏幕虚拟按键、菜单绘制、列表交互、作弊菜单 UI
GbaModels.kt             负责菜单枚举、触控按钮、存档对象、作弊码对象等基础模型
GbaNativeCheatBridge.kt  保留 native CheatManager 后续入口
```

这样 `InternalGbaActivity.kt` 不再把 UI 绘制、模型和主流程全部挤在一个 3000 多行文件里，后续看代码会清楚很多。

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

当前建议将 v0.1.56 作为 GBA 作弊码稳定断点版：

```text
GBA 开启作弊码：即时生效
GBA 关闭作弊码：自动快速存档 + 自动重启当前 GBA 游戏 + 自动快速读档
GBA 运行方式：支持内置 GBA，也支持部分外部 GBA 模拟器 App
PSP 运行方式：主要支持外部 PSP 模拟器 App
模拟器代码：已整理到 emulator/ 分平台目录，GBA 已拆分 Activity / TouchControls / Models
外部模拟器启动：统一在 system/ExternalLauncher.kt
```

如果后续 native CheatManager 无法稳定实现，就以当前方案作为 GBA 金手指正式方案，继续开发 PSP / FC / NSE 等后续模块。

### v0.1.56 多作弊码下发修复

修复同时开启多个 GBA 作弊码时，后开启的作弊码可能不生效的问题。

之前版本会把所有开启的作弊码全部合并到一个 libretro cheat slot 中：

```text
slot 0 = 穿墙代码 + 闪光代码
```

这在部分 mGBA/libretro 场景下不稳定，可能出现“先开穿墙再开闪光，穿墙正常但闪光不生效；退出重进后才正常”的情况。

v0.1.56 改为一个自定义作弊码对应一个 slot：

```text
slot 0 = 穿墙代码
slot 1 = 闪光代码
```

单个作弊码内部如果有多行，仍然使用 `+` 合并后下发，保证 My Boy! 常见多行码格式继续可用。

### v0.1.55 快速读档修复

修复关闭 GBA 作弊码后，冷重启游戏大概率没有读取最新快捷存档的问题。

关闭作弊码的顺序固定为：

```text
快速存档
↓
关闭作弊码状态
↓
冷重启当前 GBA 游戏
↓
重启完成后自动快速读档
```

同时对重启后的自动快读增加多次重试，避免 mGBA core 刚启动第一帧时读档失败。

### v0.1.54 编译修复

本版本只修复 v0.1.53 代码拆分后的 Kotlin 编译错误，不改变 GBA 金手指关闭逻辑。

修复内容：

- `GbaTouchControlsView.kt` 补充 `java.util.Locale` import。
- `InternalGbaActivity.releaseAllGameInputs()` 改为 GBA 控件可调用，修复 SELECT + X 退出组合键释放输入时的引用错误。



### v0.1.59 补充

- 编辑显示信息页的“编辑显示名称”改为弹窗输入，避免横屏页面空间不足导致输入框被压扁。
- FC/NES 外部启动增加 SAF 文档 URI 到真实外部存储路径的解析，额外尝试 file:// 路径与常见 NES MIME。
- John NESS 不再把普通启动页当作直启成功；如果当前版本未开放外部 ROM 直启入口，会提示改用 Nes.emu 或 RetroArch。


### v0.1.60 补充

- 编辑显示信息页的“移除封面”从底部移动到顶部操作区，顶部操作顺序为：移除封面 / 取消 / 保存。
- FC/NES 推荐模拟器列表移除 John NESS；John NESS 仍可在“全部”里手动选择，但因为当前版本无法稳定外部 ROM 直启，不再作为推荐项。
- FC/NES 推荐文案调整为 Nes.emu / Nostalgia.NES / RetroArch。


### v0.1.63 补充

- GBA 金手指逻辑回退到 v0.1.60 稳定方案：一个自定义作弊码对应一个 libretro cheat slot。
- 不再保留 v0.1.61/v0.1.62 的多作弊码排序/闪光穿墙组合兼容尝试。
- GBA 作弊码菜单增加非阻挡提示：穿墙和闪光同时开启可能会失效，建议最好只开一个。


### v0.1.64 补充

- FC/NES 新增内置模拟器 Phase 1。
- 历史版本曾使用 FCEUmm；v0.1.73 起内置 FC/NES 固定使用 Nestopia core：`libnestopia_libretro_android.so`。
- 新增 `InternalFcActivity.kt`、`FcTouchControlsView.kt`、`FcModels.kt`。
- 设置页中 FC/NES 增加“内置 FC/NES 模拟器”选项；清除外部模拟器后会回到内置模拟器。
- FC/NES 内置模式支持基础虚拟按键、游戏内菜单、快速存档、快速读档、快进、重启、退出。
- `.7z` 仍建议使用外部模拟器或先解压，内置 Phase 1 暂不直接解压 7z。
- 已添加 `THIRD_PARTY_NOTICES.md`，记录 Nestopia / 历史 FCEUmm core 来源和许可提醒。


### v0.1.65 补充

- FC/NES 内置模式复制 ROM 到 ASCII 缓存路径后再交给内置 core，降低中文/特殊字符文件名导致无法启动的概率。
- FC/NES 内置模式会检测 NES 头信息，遇到高 Mapper / 大容量改版 ROM 只做兼容性提示，不阻挡启动。
- FC/NES 虚拟按键调整为接近 GBA 的布局，并补充屏幕“退出”按键。
- 设置 > 系统 新增“手柄操作”，GBA / FC/NES 内置模拟器共用。
- 默认快捷键：快速保存=L1，快速读取=R1，快进=R3，菜单=L2，退出=SELECT+X，连发A=X，连发B=Y。
- 修改快捷键时支持 1~3 键组合，停止输入约 360ms 后保存；完全相同组合会弹窗提示冲突。


## v0.1.66

- 修复 v0.1.65 在部分 Compose 版本下 `nativeKeyEvent` 不可用导致的编译失败。
- 仅补兼容桥，不改变 GBA / FC/NES 模拟器逻辑。


## v0.1.69

- 修复 FC/NES 内置存档菜单文字上下重叠：存档列表改成和 GBA 一样的单行自适应列表，小屏横屏自动滚动选中项。
- FC/NES 内置启动增加无首帧渲染检测；v0.1.73 起固定使用 Nestopia 后，Mapper 115 汉化/改版 ROM 兼容性改善。
- 扩展 NES 头信息提示：遇到非普通 Mapper / NES2.0 高 Mapper / 大容量改版 ROM 时，只提示兼容性风险，不阻挡启动。

## v0.1.68

- FC/NES 内置模拟器：连接手柄并使用实体按键后，自动隐藏屏幕虚拟按键；触摸屏幕后恢复显示。
- FC/NES 内置菜单改成接近 GBA 的结构：存档、虚拟按键设置、作弊、重置、退出游戏。
- FC/NES 存档页支持 A 保存、Y 读取、X 删除，触屏也有保存 / 读取 / 删除按钮。
- FC/NES 屏幕虚拟按键布局改成更接近 GBA 内置模拟器布局。

## v0.1.67

- 修复设置页输入框会被手柄焦点卡住的问题：封面刮削输入框默认只读，不再抢占手柄焦点。
- 封面刮削输入框右侧新增“编辑 / 保存”按钮，点编辑后才能输入，点保存后重新锁定。
- 修复“设置 > 系统 > 手柄操作”页面用手柄下键选择时页面不跟随滚动的问题。
- 手柄快捷键列表拆成独立滚动项，选中下面项目时会自动滚动到可见区域。


## v0.1.73 FC/NES Nestopia only

- 手动测试确认：Nestopia 可以启动洛克人 5 Mapper 115 汉化版，也可以启动原版 ROM。
- 内置 FC/NES 固定使用 Nestopia，不再做 Mesen / FCEUmm 自动切换。
- 删除内置 FC/NES 的 Mesen / FCEUmm core 文件，降低闪退和黑屏差异。


### v0.1.73
- FC/NES 内置模拟核心固定为 Nestopia。
- 删除内置 FC/NES 的 Mesen / FCEUmm core，避免 Mesen native 闪退和 FCEUmm 对部分 Mapper 115 汉化 ROM 黑屏。
- FC/NES 核心选择页不再提供多核心切换；外部模拟器 Nes.emu / RetroArch 仍可照常使用。

## v0.1.74 内置模拟器 UI 统一规范

从 v0.1.74 开始，所有内置模拟器都必须以现有 GBA 模拟器为 UI 标准，后续不要再为 FC/NES、SFC、MD、PS1 等内置模拟器单独写一套不同风格的虚拟按键或菜单。

### 1. 公共模块位置

```text
app/src/main/java/com/bond/md3elauncher/emulator/common/
├── CommonEmulatorUiSpec.kt      # 内置菜单、提示文案、快捷键提示规范
└── CommonTouchLayout.kt         # GBA 标准虚拟按键排布生成器
CommonEmulatorHost.kt        # 后续完整公共菜单调用的功能接口
```

### 2. 虚拟按键统一规范

所有内置模拟器默认使用 GBA 标准排布：

```text
左侧：方向键 ↑ ↓ ← →
右侧：Y / X / B / A
中下：SELECT / START
辅助：L / R / 菜单 / 快存 / 快读 / 快进 / 退出
```

FC/NES 虽然只需要 A/B，但仍然保留 X/Y/L/R 的统一位置。X/Y 可以作为连发或扩展键使用。用户在不同内置模拟器之间切换时，虚拟按键位置不应变化。

### 3. 内置菜单统一规范

所有内置模拟器主菜单统一为：

```text
存档
虚拟按键设置
作弊
重置
重启游戏
退出游戏
```

顶部操作提示统一为：

```text
上下选择，A 进入，B 返回，当前退出快捷键退出
```

如果可以读取软件内手柄设置，则显示用户设置的退出组合键；读取失败时显示默认 `SELECT + X`。

### 4. 存档菜单统一规范

存档菜单统一包含：

```text
存档 1
存档 2
存档 3
存档 4
存档 5
快捷存档
```

统一操作：

```text
A = 存档
Y = 读档
X = 删除
B = 返回
```

### 5. 虚拟按键设置统一规范

虚拟按键设置统一分为：

```text
透明度设置
虚拟键编辑
```

透明度设置用于真实手柄模式下的虚拟键显示透明度。默认应为 0%，也就是使用真实手柄时不挡屏幕。虚拟键编辑用于触屏虚拟键透明度、大小、位置编辑。GBA 已有完整编辑逻辑；FC/NES 从 v0.1.74 开始接入公共排布和入口，后续继续接公共编辑器。

### 6. 重置 / 重启游戏 / 退出游戏定义

```text
重置：关闭当前模拟器核心，冷重载当前 ROM，从头开始游戏，不清除存档。
重启游戏：不退出游戏界面，调用当前 core reset，直接从头开始。
退出游戏：退出当前内置模拟器，返回启动器列表。
```

### 7. 后续开发要求

新增任何内置模拟器时必须遵守：

```text
1. 优先复用 CommonTouchLayoutBuilder.buildGbaStyleLayout。
2. 主菜单必须使用 CommonEmulatorUiSpec.MAIN_MENU_ITEMS。
3. 存档菜单必须保留 5 个普通槽 + 1 个快捷存档。
4. 作弊入口可以按模拟器能力实现，未接入时只显示“暂未支持”。
5. 不允许单独设计一套不同风格的内置菜单和虚拟按键排布。
```

### 8. v0.1.74 实际改动

```text
- 新增 emulator/common 公共 UI 规范模块。
- FC/NES 虚拟按键改为复用 GBA 标准排布生成器。
- GBA / FC/NES 主菜单统一为：存档 / 虚拟按键设置 / 作弊 / 重置 / 重启游戏 / 退出游戏。
- GBA 新增“重启游戏”入口，直接调用 core reset，不退出 Activity。
- FC/NES 增加真实手柄模式透明度设置，手柄模式默认隐藏虚拟键。
- FC/NES 继续固定使用 Nestopia core。
```

## v0.1.75 Launcher list reorder and fixed launcher hotkeys

- 设置 > 系统 已移除「FC/NES 模拟核心」入口。FC/NES 内置模拟器继续固定使用 Nestopia core，不再在设置里展示核心选择。
- 启动器底部按键提示增加固定顺序调整入口：
  - `L3 / 左摇杆按下`：上移当前选中的条目。
  - `R3 / 右摇杆按下`：下移当前选中的条目。
- 上移 / 下移只作用于当前顶部游戏 / 应用列表的显示顺序，不影响模拟器内部按键绑定，不占用「设置 > 系统 > 手柄操作」里的快捷键。
- 条目已经置顶时，上移按钮显示为灰色不可用；条目已经在底部时，下移按钮显示为灰色不可用。
- 搜索结果页面不允许调整顺序，避免只移动过滤后的局部列表导致顺序混乱。
- 排序会保存到本地：收藏、各游戏平台列表、安卓游戏列表、全部应用列表分别使用独立排序。
- 后续新增模拟器或新的列表页面时，应该继续复用这套固定规则：底部显示 `Y 设置 / X 搜索 / B 返回或收藏 / L3 上移 / R3 下移 / A 启动`，列表顺序调整不得再绑定到方向键，方向键只用于正常移动焦点。


## v0.1.76 补充

- 新增 GB/GBC 平台，支持 `.gb/.gbc/.sgb/.zip/.7z` 扫描。
- GB/GBC 默认使用内置 mGBA core 启动；GBA 平台不变。
- `.zip` 内置启动时现在支持解出 `.gb/.gbc/.sgb`。
- 启动器底部提示顺序固定为 `Y 设置 / X 搜索 / L3 上移 / R3 下移 / B 操作`，B 的文案变化不再影响前面按钮位置。
- 列表上移 / 下移后会延迟滚动到当前选中项，修复移动到顶部时选中游戏看不到的问题。

## v0.1.77 设置说明与编辑封面提示

- `设置 > 系统 > 手柄操作` 的简介统一精简为：`设置内置模拟器通用快捷键，支持1~3键组合。`
- `设置 > 系统 > 手柄操作` 详情页顶部说明也使用同一条短文案，避免占用过多空间。
- 游戏列表长按进入 `编辑显示信息` 后，在 `联网搜索封面 / 设备选择封面 / 编辑显示名称` 下方增加封面尺寸提示：建议竖版 3:4，推荐 `600×800 px`。
- 编辑显示信息页右侧操作区允许垂直滚动；后续再增加按钮或提示时，不允许因为内容变多导致底部溢出。
- 封面图片规范：推荐 PNG/JPG，竖版 3:4；过大的图片由界面使用 `ContentScale.Fit` 自动适配显示，不要强制裁剪用户图片。
