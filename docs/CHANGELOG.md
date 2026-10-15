# Changelog

Only current and high-value milestones are retained here. Detailed experimental notes were removed from the handoff package to keep project context focused.

## 1.3.1

- 修复刮削凭据输入框旁的“保存”只退出编辑、未保存配置的问题；TheGamesDB 认证和额度错误现在会明确显示。
- 修正 PSP 的 TheGamesDB 平台编号；中英文搜索使用统一接口，封面与真实截图分开获取，候选显示游戏标题。
- 平台批量补全接入 TheGamesDB 的发行日期、类型、开发商、发行商、封面和截图；只补缺失项，系列名和歧义匹配不会自动套用。
- Glass 开关使用玻璃滑块；清理重复材质、边框和按钮叠层，修正深色图标、输入框/按钮高度、边距加减及底部留白。
- Glass、MIUIX 新增“背景流动效果”开关，默认关闭，设置持久保存且兼容备份，退到后台暂停动画。
- 验证范围与限制见 `docs/UI_REVIEW_1_3_1.md`。

## 1.3.0

- **游戏资料**：长按游戏 → 编辑游戏资料，支持发行日期、类型、开发商和发行商；资料显示在右侧预览下方，随备份保存，重扫不会清除。
- **平台批量补全**：设置 → 模拟器 → 平台配置 → 批量补全资料与图片，只补缺失封面、截图和资料，显示进度并可取消。已完成项目立即保存，重试时继续补缺。
- **三种界面风格**：设置 → 外观选择 M3、Glass 或 MIUIX。M3 保留莫奈主题；Glass 提供模糊、折射、表面不透明度设置；MIUIX 跟随系统深浅色，不显示额外外观选项。
- 平台管理与模拟器列表移除序号，长按排序和手柄操作保留。
- 新版 UI 依赖要求最低 Android 7.0。Glass 在 Android 12 起支持模糊，Android 13 起支持折射，旧系统使用有边界的半透明表面；游戏画面、封面和截图保持清晰。

- 补齐按钮横向/纵向间距、Glass 胶囊滑块、MIUIX 输入提示；验证记录见 `docs/UI_REVIEW_1_3_0.md`。

## 1.2.0

- 新增 PS2、Nintendo DS、Nintendo 3DS、GameCube、Wii、Dreamcast 外部模拟器平台；“添加其他”现在可以进入实际配置。
- 平台可选自定义 Activity、Action、MIME、URI 模板及 JSON 附加参数；默认保留自动适配，换模拟器时重置旧的自定义配置。
- 模拟器选择页增加刷新，窄屏操作按钮可以换行。
- Libretro 预览图搜索使用游戏截图，宫格图搜索使用封面；预览与宫格自定义图片保持独立。
- 新平台仅提供前端扫描与外部启动入口，未增加内置核心；具体模拟器的运行兼容性仍取决于其 URI 与启动参数支持。
- Beacon 功能对比与后续计划见 `docs/BEACON_COMPARISON.md`；元数据展示、批量刮削、多 ROM 目录及单游戏启动覆盖尚未实现。

## 1.1.1

- Move backup, about and version actions to the bottom of Settings with equal-width entries.
- Unify section headings, cards, spacing and text hierarchy; separate appearance from display/margins.
- Place margin controls side by side on wide layouts and wrap language/orientation choices when needed.
- Make toggle rows clickable and disable rescan actions while scanning.
- Fit long scraper field labels and add 16dp of space before the explanatory text.

## 1.1.0

- Add recent games/apps, background artwork decoding and shared image caching.
- Preserve the library on failed or incomplete scans; recover individual malformed records and retain a previous valid library.
- Add versioned ZIP backup/restore for settings, artwork and internal emulator saves, with staging, rollback and credential exclusion.
- Add compact backup/about/version entries, offline legal notices, project credits and optional sponsorship addresses.
- Add on-demand GitHub update checks, ABI selection, bounded downloads, SHA-256/package/signature/version verification and Android installer handoff.
- Extend Libretro platform coverage and connect ScreenScraper with user-provided developer credentials.
- Add game launch preflight checks with a direct platform settings action.
- Synchronize English, Simplified Chinese and Traditional Chinese UI text; improve the README.

## 1.0.2

- Refresh the installed Android app list every time Android app management is opened from Settings.
- Newly installed launchable apps now appear without restarting GameHub.
- Preserve existing Android game tags, favorites, ordering and launch behavior during refresh.

## 1.0.1

- Fixed Monet dynamic colors being bypassed while the launcher was in dark mode.
- Changed the default manual theme color to pink.
- Added persistent pink, blue, purple, green and orange theme presets when Monet is disabled.
- Kept dynamic wallpaper colors active on Android 12 and later while preserving the existing launcher layout and controls.

## 1.0.0

- Completed the planned built-in emulator lineup: GBA, GB/GBC, FC/NES, SFC/SNES, MD/Genesis, PS1, N64 and Arcade.
- Added per-platform selection between built-in and supported external emulators.
- Added N64 header detection and automatic byte-order normalization for `.z64`, `.v64`, `.n64` and incorrectly named compatible files.
- Added the MAME 2003-Plus Arcade runtime with six-button touch controls and shared save/menu behavior.
- Added ARM64 and ARM32 release APK splits to reduce download size without removing emulator features.
- Preserved launcher list/grid layouts, right-side preview artwork, favorites, custom ordering and controller shortcuts.
- Added release signing support, synchronized English/Simplified Chinese/Traditional Chinese text and refreshed public documentation.

## 0.1.93

- Reworked the display editor into separate preview-artwork and grid-artwork cards.
- Moved display-name editing into the top current-name control.
- Kept wide layouts side by side and narrow layouts scrollable.
- Removed irrelevant footer actions from detail/back screens.
- Synchronized new text across all three i18n files.

## 0.1.92

- Split custom artwork into `previewImagePath` and `gridImagePath` with legacy migration.
- Moved list/grid controls to icon buttons beside the launch hint.
- Added tablet-first grid and phone-first list defaults while preserving user choice.

## 0.1.91

- Added list/grid layouts across launcher collections.
- Added responsive 1–4 column grid while retaining the right preview.
- Tightened non-Chinese footer shortcut spacing.

## 0.1.90

- Separated physical-controller opacity, touch-control opacity and virtual-button editing.
- Shared internal control preferences across supported built-in emulators.

## 0.1.88

- Added SFC/SNES platform and internal Snes9x core path.
- Reused the common internal emulator menu, save and touch-control conventions.

## 0.1.87

- Standardized the user-visible brand as GameHub.
- Kept package identity `com.bond.md3elauncher` for upgrade/data compatibility.

## Key earlier milestones

- **0.1.85:** synchronized language state across separate emulator processes and closed emulator processes on normal exit.
- **0.1.78–0.1.82:** established JSON-based English, Simplified Chinese and Traditional Chinese UI text.
- **0.1.76:** added GB/GBC as an independent launcher platform using the mGBA runtime.
- **0.1.74:** introduced common internal emulator menu and GBA-style virtual-control rules.
- **0.1.73:** fixed FC/NES internal emulation on Nestopia only.
- **0.1.64:** added the first internal FC/NES path.
- **0.1.56:** stabilized multiple GBA cheats by assigning one custom cheat per libretro slot.
- **0.1.55:** stabilized cheat disabling with quick-save, restart and automatic quick-load.
