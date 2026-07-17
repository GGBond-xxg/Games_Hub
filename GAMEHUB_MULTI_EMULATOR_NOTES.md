## v0.1.93 Edit display page rule

- Keep preview and grid artwork as separate image slots, but group each slot's image, usage description, online picker, device picker, and restore action inside one card.
- Display-name editing belongs in the top current-name button; do not add another full-width name action below the artwork cards.
- On wide landscape layouts, the preview and grid cards must stay side by side and fill the remaining edit-page height. Narrow layouts may stack and scroll.
- Back/detail pages only show bottom hints for actions that are currently available. Do not render disabled X / L3 / R3 / A hints or duplicate the edit-page title in the footer.
- All new visible strings must remain synchronized across `en.json`, `zh.json`, and `zh-Hant.json`.

## v0.1.92 Launcher artwork and layout rule

- `ItemOverride` separates `previewImagePath` and `gridImagePath`. The preview image is only for the large right-side preview; the grid image is for left-side game cards.
- Legacy `imagePath` data must be migrated to both image fields when loading so existing custom artwork is not lost.
- Long-press edit actions are arranged as two columns: online preview / online grid, device preview / device grid, with display-name editing on a full-width row.
- List / grid switching uses icon-only controls beside the A launch hint in the bottom bar. Do not restore the old text switcher below the content area.
- First launch defaults to GRID on tablet-class devices (`smallestScreenWidthDp >= 600`) and LIST on phones. Once the user switches manually, the stored choice takes priority.
- Grid remains capped at four columns and the right-side preview remains visible. L3 / R3 ordering, long-press A edit, A launch, and B favorite / add behavior stay identical in both modes.

## v0.1.91 Launcher layout rule (historical, superseded by v0.1.92)

- 收藏、游戏平台、安卓游戏、全部应用统一支持列表 / 宫格切换。
- v0.1.91 used a text switcher below the content area; v0.1.92 moves it to icon buttons beside A.
- 宫格最多 4 列，仍保留右侧竖向预览。
- 非中文底部按键只显示圆形键帽，并使用紧凑间距。


## v0.1.80 I18N / 文本规范补充

1. 语言来源：设置 > 系统 > 语言，可选跟随系统、English、简体中文、繁體中文。
2. 设备语言不是 zh / zh-Hant 时默认使用 English。
3. 非中文语言下主界面底部只显示按键圆点，不显示操作文案，防止英文/其他语言过长。
4. 主界面、设置、平台配置、模拟器选择、封面搜索等普通 App UI 文本必须走 JSON。
5. 内置模拟器运行时菜单仍按公共 UI 模块逐步迁移；后续新加菜单必须优先复用 common UI + I18N key。

# v0.1.47 Notes

- GBA 关闭作弊码时改为冷重启 `:internal_gba` 进程，重新创建 mGBA core。
- 解决穿墙码这类 ROM/指令 patch 在当前 core 内无法通过 `resetCheat()` 释放的问题。
- 关闭后尝试恢复开启作弊前的 clean state。
- 继续保留自定义作弊码 UI：添加、A 启用/关闭、X 删除、B 返回。


# v0.1.57 Notes

- 新增 FC/NES 平台入口，顶部平台标签显示为 `FC`。
- 新增 `PlatformKind.NES`，支持 `.nes/.fds/.unf/.unif/.zip/.7z`。
- FC/NES 当前不做内置 core，先走外部模拟器 App 方案。
- 新增 `emulator/fc/FcExternalEmulatorProfiles.kt`，集中维护 FC/NES 外部模拟器关键词和 RetroArch core 候选。
- 外部启动适配 Nes.emu / Nostalgia.NES / John NESS / RetroArch 等。
- RetroArch core 候选：FCEUmm / Nestopia / QuickNES / Mesen。


# v0.1.58 Notes

- 编辑显示信息页重排：顶部一行显示返回、编辑显示信息、当前游戏名省略、取消、保存。
- “显示名称”改为点击“编辑显示名称”后展开输入框，并去掉禁止焦点逻辑，修复名称无法编辑。
- FC/NES 外部启动增加 Nes.emu `com.imagine.BaseActivity` 专用候选。
- FC/NES 外部启动增加 John NESS / John NES 包名、Activity 与常见 ROM path extras 候选。
- John NESS 若仍然只进入 ROM 列表，属于模拟器本身没有公开稳定直启接口，建议换 Nes.emu 或 RetroArch 测一键直启。


# v0.1.59 Notes

- Edit display name now uses a Material3 dialog instead of inline TextField.
- FC/NES external launch tries resolved external-storage file paths in addition to content:// URIs.
- John NESS launcher/list activity is no longer treated as direct ROM launch success.


# v0.1.60 Notes

- Move the edit-page remove-cover action from the lower content area to the top action row before Cancel / Save.
- Remove John NESS from FC/NES recommended emulator keywords and help text because testing showed it does not provide a stable direct ROM launch entry.
- Keep John NESS package detection in the guarded launcher path for users who manually select it from the full app list.


# v0.1.63 Notes

- Based on v0.1.60 source state for GBA cheat runtime logic.
- Added a warning-only hint in the GBA cheat menu: walk-through-walls and shiny cheats may conflict when enabled together; users are advised to enable only one.
- No blocking behavior was added; users can still enable both cheats manually.

# v0.1.64 Notes

- FC/NES now has an internal emulator Phase 1. Since v0.1.73 it uses only the Nestopia libretro core.
- Added `InternalFcActivity.kt`, `FcTouchControlsView.kt`, and `FcModels.kt` under `emulator/fc/`.
- Added `libfceumm_libretro_android.so` for arm64-v8a, armeabi-v7a, x86, and x86_64.
- FC/NES platform settings now include “内置 FC/NES 模拟器”; clearing an external FC/NES emulator falls back to the internal emulator.
- External FC/NES launch remains available; Nes.emu remains confirmed as a working one-click direct-launch option.
- Internal FC/NES Phase 1 supports `.nes` directly and tries to extract `.nes/.fds/.unf/.unif` from ordinary `.zip`; `.7z` still needs external emulator or manual extraction.


# v0.1.65 Notes

- Added shared internal-emulator controller shortcut settings under Settings > System > 手柄操作.
- GBA and FC/NES now read the same shortcut bindings.
- FC/NES touch controls were adjusted closer to the GBA layout and now include an on-screen exit button.
- FC/NES internal ROM preparation now passes an ASCII cache path to the selected internal core to avoid native-path problems with Chinese or special-character filenames.
- Some translated or hacked NES ROMs may still require a more compatible NES core; Nes.emu external launch remains the recommended fallback for those cases.


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

- 设置页封面刮削输入框改为“编辑 / 保存”模式，避免手柄焦点进入输入框后卡住。
- 手柄操作页面按方向键选择快捷键时，列表会自动滚动到当前选中项目。


## v0.1.73 FC/NES Nestopia only

- 手动测试确认：Nestopia 可以启动洛克人 5 Mapper 115 汉化版，也可以启动原版 ROM。
- 内置 FC/NES 固定使用 Nestopia，不再做 Mesen / FCEUmm 自动切换。
- 删除内置 FC/NES 的 Mesen / FCEUmm core 文件，降低闪退和黑屏差异。


### v0.1.73
- FC/NES 内置模拟核心固定为 Nestopia。
- 删除内置 FC/NES 的 Mesen / FCEUmm core，避免 Mesen native 闪退和 FCEUmm 对部分 Mapper 115 汉化 ROM 黑屏。
- FC/NES 核心选择页不再提供多核心切换；外部模拟器 Nes.emu / RetroArch 仍可照常使用。

## v0.1.74 公共 UI 模块化说明

- 新增 `emulator/common/CommonEmulatorUiSpec.kt`：统一内置菜单、存档数量、虚拟按键设置入口、手柄退出快捷键提示。
- 新增 `emulator/common/CommonTouchLayout.kt
emulator/common/CommonEmulatorHost.kt`：统一 GBA 标准虚拟按键排布。
- FC/NES 现在复用公共 GBA 样式排布；GBA 作为标准源继续保持现有稳定操作。
- 所有后续内置模拟器必须先复用公共模块，再实现自己的 save/load/reset/cheat 逻辑。

## v0.1.75 Launcher reorder rule

- 启动器列表顺序调整固定为 `L3 = 上移`、`R3 = 下移`。
- 这个规则只属于启动器主页 / 平台页 / 收藏页 / 安卓列表，不属于内置模拟器菜单和手柄快捷键设置。
- 不允许把上移 / 下移绑定到方向键；方向键保留给焦点移动。
- 底部提示统一显示 `L3 上移`、`R3 下移`，置顶 / 置底时对应按钮灰色禁用。
- 排序数据使用独立 scope 保存，例如 `favorites`、`platform:GBA`、`platform:NES`、`android_games`、`android_all`。


## v0.1.76 GB/GBC + Launcher footer scroll fix

- 新增 `PlatformKind.GB`，顶部标签为 `GB`，平台名称为 `GB/GBC`。
- 默认平台列表和默认顶部排序加入 GB/GBC：`PSP / NS / GBA / GB / FC / 安卓`。
- GB/GBC 默认使用内置 mGBA core，不新增单独 native core。
- `InternalGbaActivity` 现在支持 `.gb/.gbc/.sgb` 直读，以及普通 `.zip` 中解出 GB/GBC ROM。
- 底部提示顺序调整为 `Y 设置 / X 搜索 / L3 上移 / R3 下移 / B 操作`。
- 列表上移 / 下移后延迟滚动到当前选中项，避免移动到顶部后当前游戏被滚出可视区域。
- 后续新增模拟器仍必须复用 `emulator/common/` 的公共按键和菜单规范。

## v0.1.77 UI wording and cover hint rule

- Settings > System > 手柄操作 subtitle and detail description must stay short: `设置内置模拟器通用快捷键，支持1~3键组合。`
- Long-press edit page must show a cover recommendation below the three actions: online cover search, local cover selection, and display name edit.
- Recommended cover size is portrait 3:4, `600×800 px`; PNG/JPG are both acceptable.
- The edit action column must remain vertically scrollable to prevent overflow when future hints or buttons are added.

## v0.1.78 / v0.1.79 i18n / JSON 文本规范

- 用户可见文本逐步迁移到 `app/src/main/assets/i18n/*.json`。
- 当前维护三份：`en.json`、`zh.json`、`zh-Hant.json`。
- 默认语言是英文；简体中文系统显示 `zh.json`，繁体中文系统显示 `zh-Hant.json`，其他语言显示 `en.json`。
- 读取统一使用 `I18n.t(context, key, fallback)`。
- 所有新增 UI 文本必须三份 JSON 同步补 key。
- 长文本必须使用 `maxLines + TextOverflow.Ellipsis` 或 Canvas 的 fitted text 逻辑。
- 内置模拟器公共菜单已经通过 `CommonEmulatorUiSpec` 接入 JSON，后续模拟器必须复用该模块。

## v0.1.79 编译修复 / 繁体 JSON

- 修复 SettingsScreens.kt 缺少 LocalContext.current 的编译错误。
- 新增 `zh-Hant.json`。
- `I18n.languageFor()` 现在按系统语言判断：繁体中文 -> `zh-Hant`，其他中文 -> `zh`，其他语言 -> `en`。


## v0.1.81
Completed i18n cleanup baseline. All new visible text must be JSON based.


## v0.1.82 i18n emulator cleanup

Built-in emulator visible text must not be hardcoded in Kotlin. GBA/GB/GBC and FC/NES menu labels, save-state labels, virtual button editor text, and toast messages are now routed through assets/i18n/en.json, zh.json, and zh-Hant.json. Chinese aliases inside parser code may remain only as accepted input aliases and must not be displayed directly.

## v0.1.85 internal emulator process lifecycle

GBA / GB/GBC uses `:internal_gba`; FC/NES uses `:internal_fc`. These processes can keep old in-memory caches, including language settings. Normal emulator Exit now kills the internal process after finishing the Activity. Language mode is also written to `filesDir/i18n_language_mode.txt` for cross-process reads.

## v0.1.88 SFC/SNES notes

SFC/SNES is now part of the common emulator roadmap and should follow the same built-in emulator UI rules as GBA, GB/GBC and FC/NES.

Implementation rules:
- Built-in core: `libsnes9x_libretro_android.so`.
- Platform kind: `PlatformKind.SFC`.
- Top tab: `SFC`.
- Display name: `SFC/SNES`.
- ROM extensions: `.sfc`, `.smc`, `.swc`, `.fig`, `.bs`, `.st`, `.zip`, `.7z`.
- Direct 7z loading is listed for scanning but not loaded by the internal core yet; users should extract 7z or use an external emulator.
- Use the shared internal emulator menu and GBA-style virtual button layout.

## v0.1.89 - My OldBoy! external launch note

Some My OldBoy! builds only expose their normal launcher / ROM browser to other apps. GameHub now tries known My OldBoy! emulator activities and exported ROM VIEW activities first. If the installed build still opens only My OldBoy! itself, use GameHub's built-in GB/GBC emulator for true one-click launch.

## v0.1.90 Virtual Controls Rule
All built-in emulators must treat physical-controller opacity and touch-controller opacity as two separate settings:

1. Real Controller Opacity
   - Used after a hardware controller is detected.
   - Default can be 0% so virtual controls do not block the screen.

2. Virtual Controller Opacity
   - Used when the player is using on-screen touch controls.
   - Should remain visible enough for touch play.

3. Virtual Button Editor
   - Used for button position, size, and custom combo button editing.

GBA and GB/GBC already share these settings. FC/NES and SFC/SNES must also use the common internal virtual-control preference store so opacity and layout behavior remain consistent.
