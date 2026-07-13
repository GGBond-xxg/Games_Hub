# FC/NES Emulator Area

v0.1.64 起，FC/NES 开始接入 **内置模拟器 Phase 1**。

当前路线：

1. 内置 FC/NES 模拟器
   - v0.1.73 起固定使用 Nestopia libretro core：`libnestopia_libretro_android.so`
   - 新增 `InternalFcActivity.kt`
   - 新增 `FcTouchControlsView.kt`
   - 新增 `FcModels.kt`

2. 外部 App 模拟器
   - Nes.emu
   - Nostalgia.NES
   - RetroArch
   - 其他支持 ACTION_VIEW / ACTION_SEND 打开 ROM 的模拟器

当前代码：

```text
InternalFcActivity.kt              # 内置 FC/NES Activity、ROM 启动、快存快读
FcTouchControlsView.kt             # FC/NES 虚拟按键、菜单绘制、触摸交互
FcModels.kt                        # FC/NES 存档、触控按钮、虚拟键模型
FcExternalEmulatorProfiles.kt      # FC/NES 外部模拟器关键词和 RetroArch core 候选
```

当前支持扫描的 ROM 扩展名仍在 `PlatformKind.NES` 中配置：

```text
.nes / .fds / .unf / .unif / .zip / .7z
```

内置 FC/NES Phase 1 说明：

- `.nes` 可直接读取。
- 普通 `.zip` 会尝试解出里面的 `.nes / .fds / .unf / .unif`。
- `.7z` 目前不在内置模拟器里直接解压；需要先解压成 `.nes`，或者继续使用 Nes.emu / RetroArch 外部模拟器。
- `.fds` 可能需要 FDS BIOS，当前 Phase 1 只做 core 接入，不额外内置 BIOS。

John NESS 说明：

测试确认当前版本无法稳定通过外部 Intent 直启 ROM，因此不放入 FC/NES 推荐列表。如果从“全部”里手动选择，启动器仍会拦截并提示优先改用 Nes.emu 或 RetroArch。

## v0.1.65 补充

- 内置 FC/NES 启动时不再直接使用 ROM 原文件名作为 native core 路径，会复制为 ASCII 缓存文件名，减少中文/特殊字符路径导致的启动失败。
- FC/NES 触控按键已补充退出按键，整体布局向 GBA 内置模拟器靠拢。
- 手柄快捷键改为和 GBA 共用的全局设置：设置 > 系统 > 手柄操作。

## v0.1.70 补充

- 针对 Mapper 115 汉化/改版 ROM，内置 FC/NES 改为干净启动，不再默认自动读取快捷存档。
- 如果之前启动过黑屏并生成了快捷存档，旧快照不会再自动覆盖新启动流程；需要读档时从游戏内菜单手动读取。

## v0.1.73 补充

- 内置 FC/NES 固定使用 Nestopia core。实测普通 ROM 与 Mapper 115 汉化/改版 ROM 均可启动。
- 已移除 Mesen / FCEUmm 内置 FC/NES core，避免多核心自动切换带来的闪退/黑屏差异。
- 设置页不再提供 FC/NES 多核心切换，只显示当前固定核心 Nestopia。
