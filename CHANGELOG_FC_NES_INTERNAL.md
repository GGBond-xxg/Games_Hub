# FC/NES Internal Emulator Changelog

## v0.1.73 Nestopia only

- FC/NES 内置模拟器固定使用 Nestopia core。
- 已删除内置 FC/NES 的 Mesen / FCEUmm core 文件，只保留 `arm64-v8a/libnestopia_libretro_android.so`。
- 设置页不再提供 FC/NES 多核心选择，只显示当前固定核心 Nestopia。
- 保留 GBA 的 mGBA core，不影响 GBA。


## v0.1.72

- FC/NES：加入用户提供的 Android arm64-v8a 新版 libretro cores：Mesen、Nestopia、新版 FCEUmm。
- FC/NES：Mapper 115 / 汉化改版 ROM 自动优先尝试 Mesen，其次 Nestopia，最后回退 FCEUmm。
- FC/NES：设置 > 系统 新增「FC/NES 模拟核心」，支持自动、Mesen、Nestopia、FCEUmm 手动切换。
- FC/NES：替换 arm64-v8a 下的 FCEUmm core 为用户从 Libretro buildbot 下载的新版 core；其他 ABI 仍保留原 core。
- GBA 逻辑不变。

## v0.1.71

- FC/NES：启动汉化 / 改版 ROM 时不再连续弹出 Mapper 风险提示，相关信息仅写入日志。
- FC/NES：菜单新增「重启游戏」，通过 libretro soft reset 在不退出当前游戏界面的情况下重新开始。
- FC/NES：「重置」保留为冷重载当前 ROM，用于 soft reset 无法恢复时兜底。

# FC/NES Internal Emulator Changelog

## v0.1.70

- 根据汉化版洛克人启动日志调整内置 FC/NES：检测到 Mapper 115 时，明确按高风险汉化/改版 ROM 处理。
- FC/NES 内置启动默认改为“干净启动”，不再自动读取快捷存档或存档 1，避免旧快照/黑屏快照被自动恢复导致每次启动都黑屏。
- 保留手动读档：进入游戏后仍可通过菜单 > 存档 手动读取快捷存档或普通存档。
- 增加 NES core 候选选择框架：如果 APK 后续打包 `libmesen_libretro_android.so` 或 `libnestopia_libretro_android.so`，高风险 Mapper 会优先尝试 Mesen / Nestopia；当前包未包含这些 core 时仍回退 FCEUmm。
- GBA 逻辑不变。

## v0.1.65

- FC/NES 内置模拟器启动 ROM 时，统一复制到 ASCII 缓存文件名，避免中文/特殊字符路径传给 libretro core 后无法启动。
- FC/NES 内置模拟器增加 ROM 头信息检测；遇到 NES2.0 高 Mapper 或大容量改版 ROM 时只提示兼容性风险，不阻挡启动。
- FC/NES 虚拟按键布局调整为更接近 GBA：左侧方向键、右侧 A/B 与 X/Y 连发、顶部菜单/快存/快读/快进/退出。
- 新增全局内置模拟器手柄快捷键配置：设置 > 系统 > 手柄操作。
- GBA 与 FC/NES 共用快捷键配置，默认：快速保存=L1，快速读取=R1，快进=R3，菜单=L2，退出=SELECT+X，连发A=X，连发B=Y。
- 手柄快捷键支持 1~3 键组合；修改时停止输入约 360ms 自动保存，并在完全相同组合冲突时弹窗提示。
- GBA 金手指逻辑不再改动，继续保持 v0.1.63 的稳定方案与提示。

## v0.1.64

- 新增内置 FC/NES 模拟器 Phase 1。
- 使用用户提供的 `lemuroid-cores.zip` 中的 FCEUmm libretro core。
- 新增 core 文件：
  - `app/src/main/jniLibs/arm64-v8a/libfceumm_libretro_android.so`
  - `app/src/main/jniLibs/armeabi-v7a/libfceumm_libretro_android.so`
  - `app/src/main/jniLibs/x86/libfceumm_libretro_android.so`
  - `app/src/main/jniLibs/x86_64/libfceumm_libretro_android.so`
- 新增代码：
  - `emulator/fc/InternalFcActivity.kt`
  - `emulator/fc/FcTouchControlsView.kt`
  - `emulator/fc/FcModels.kt`
- FC/NES 平台设置页新增“内置 FC/NES 模拟器”。
- FC/NES 默认可使用内置模拟器；外部 Nes.emu / RetroArch 等仍可继续使用。
- 内置 FC/NES 支持基础虚拟按键、游戏内菜单、快速存档、快速读档、快进、重启、退出。
- `.nes` 可直接运行；普通 `.zip` 会尝试解出 `.nes / .fds / .unf / .unif`；`.7z` 暂不支持内置直接解压。
- 添加 `THIRD_PARTY_NOTICES.md` 和 `third_party/lemuroid/COPYING`，记录 Lemuroid / FCEUmm 来源与许可提醒。


## v0.1.66

- 修复 v0.1.65 在部分 Compose 版本下 `nativeKeyEvent` 不可用导致的编译失败。
- 仅补兼容桥，不改变 GBA / FC/NES 模拟器逻辑。


## v0.1.69

- 修复 FC/NES 内置存档菜单文字上下重叠：存档列表改成和 GBA 一样的单行自适应列表，小屏横屏自动滚动选中项。
- FC/NES 内置启动增加无首帧渲染检测：部分汉化/改版 ROM 如果 FCEUmm core 没有渲染画面，会提示使用 Nes.emu 外部模拟器。
- 扩展 NES 头信息提示：遇到非普通 Mapper / NES2.0 高 Mapper / 大容量改版 ROM 时，只提示兼容性风险，不阻挡启动。

## v0.1.68

- FC/NES 内置模拟器：连接手柄并使用实体按键后，自动隐藏屏幕虚拟按键；触摸屏幕后恢复显示。
- FC/NES 内置菜单改成接近 GBA 的结构：存档、虚拟按键设置、作弊、重置、退出游戏。
- FC/NES 存档页支持 A 保存、Y 读取、X 删除，触屏也有保存 / 读取 / 删除按钮。
- FC/NES 屏幕虚拟按键布局改成更接近 GBA 内置模拟器布局。

## v0.1.67

- 修复设置页输入框会被手柄焦点卡住的问题，封面刮削输入框默认只读，需要点右侧“编辑”后才可输入。
- 修复手柄操作页面用方向键切换快捷键项目时，页面不自动滚动导致看不到下面项目的问题。


## v0.1.73 FC/NES Nestopia only

- 手动测试确认：Nestopia 可以启动洛克人 5 Mapper 115 汉化版，也可以启动原版 ROM。
- 内置 FC/NES 固定使用 Nestopia，不再做 Mesen / FCEUmm 自动切换。
- 删除内置 FC/NES 的 Mesen / FCEUmm core 文件，降低闪退和黑屏差异。


### v0.1.73
- FC/NES 内置模拟核心固定为 Nestopia。
- 删除内置 FC/NES 的 Mesen / FCEUmm core，避免 Mesen native 闪退和 FCEUmm 对部分 Mapper 115 汉化 ROM 黑屏。
- FC/NES 核心选择页不再提供多核心切换；外部模拟器 Nes.emu / RetroArch 仍可照常使用。

## v0.1.74

- FC/NES 虚拟按键排布改为复用公共 GBA 标准布局生成器。
- FC/NES 主菜单继续保持：存档 / 虚拟按键设置 / 作弊 / 重置 / 重启游戏 / 退出游戏。
- 新增真实手柄模式虚拟键透明度设置，默认 0%，使用实体手柄时不挡屏幕。
- FC/NES 继续固定使用 Nestopia core，不再自动切换 Mesen/FCEUmm。
- 新增 README 级别的后续内置模拟器 UI 规范，防止后续聊天或接手时风格跑偏。

## v0.1.75 Launcher settings cleanup

- 设置 > 系统 移除「FC/NES 模拟核心」。FC/NES 仍固定使用 Nestopia core。
- 启动器底部新增 `L3 上移`、`R3 下移`，用于调整当前游戏 / 应用列表顺序。
