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
