# FC/NES Emulator Area

当前从 v0.1.57 开始实现 **外部 FC/NES 模拟器启动方案**，不是内置 FC/NES core。v0.1.58 增加 John NESS / Nes.emu 专用启动候选；v0.1.59 继续优化 SAF 路径直启尝试，并避免 John NESS 只打开自身列表页时被误判为直启成功；v0.1.60 起推荐列表移除 John NESS，优先推荐 Nes.emu / Nostalgia.NES / RetroArch。

当前代码：

```text
FcExternalEmulatorProfiles.kt
```

它负责集中维护：

- 推荐外部模拟器关键词
- FC/NES 相关包名匹配
- RetroArch FC/NES core 候选

当前支持的 ROM 扩展名在 `PlatformKind.NES` 中配置：

```text
.nes / .fds / .unf / .unif / .zip / .7z
```

当前启动流程在 `system/ExternalLauncher.kt` 中执行：

```text
扫描 FC/NES ROM
↓
选择外部模拟器 App
↓
ACTION_VIEW / ACTION_SEND / content:// 授权 Uri / 专用 Activity 候选
↓
外部模拟器打开 ROM

John NESS 说明：测试确认当前版本无法稳定通过外部 Intent 直启 ROM，因此不再放入 FC/NES 推荐列表；如果用户从“全部”里手动选择，启动器仍会拦截并提示优先改用 Nes.emu 或 RetroArch。
```

后续如果做内置 FC/NES 模拟器，可以在这个目录继续新增：

```text
InternalFcActivity.kt
FcTouchControlsView.kt
FcModels.kt
FcCoreBridge.kt
```

建议优先考虑 FCEUmm / Nestopia / QuickNES / Mesen 这类 libretro core。
