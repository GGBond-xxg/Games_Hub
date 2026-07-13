# GBA Cheat Changelog

## v0.1.56

- 修复同时开启多个 GBA 作弊码时，后开启的作弊码可能不生效的问题。
- 原因：之前把所有已启用作弊码合并到同一个 `setCheat(0, ...)`，mGBA/libretro 对多组 GameShark/CodeBreaker 码放在同一个 slot 时，容易只让前一组稳定生效。
- 新策略：一个自定义作弊码对应一个 libretro cheat slot；单个作弊码内部多行仍使用 `+` 合并。
- 例如穿墙和闪光同时开启时，下发方式从 `slot0=穿墙+闪光` 改成 `slot0=穿墙`、`slot1=闪光`。
- 同时修复 `saveQuickStateBeforeCheatRestart()` 中重复声明 state 的问题。

## v0.1.55

- 修复关闭作弊码后冷重启游戏大概率没有读取最新快捷存档的问题。
- 关闭作弊码流程调整为：快速存档 → 关闭作弊码状态 → 冷重启当前 GBA 游戏 → 重启后自动快速读档。
- 自动快读增加多次重试，降低 mGBA core 刚启动时读档失败的概率。
- 快捷存档写入时增加 `fd.sync()`，避免进程随后被 kill 时文件还没完全落盘。
- 作弊菜单说明改为“关闭：先快存，重启后自动快读”。

## v0.1.54

- 修复 v0.1.53 代码拆分后的 Kotlin 编译错误。
- `GbaTouchControlsView.kt` 补充 `java.util.Locale` 引用。
- `releaseAllGameInputs()` 改为 `InternalGbaActivity` 对 GBA 控件可见，修复 SELECT + X 退出组合键释放输入时的引用错误。
- 不改变 v0.1.51/v0.1.53 的 GBA 金手指稳定方案。


## v0.1.53

- 优化 GBA 作弊码菜单文字重叠问题：作弊页不再同时绘制顶部长提示和说明文字。
- 作弊码列表文字增加自动省略，避免长作弊码把 UI 挤乱。
- 将 GBA 内置模拟器代码继续拆分：
  - `InternalGbaActivity.kt`：主流程、ROM、存档、作弊码执行。
  - `GbaTouchControlsView.kt`：虚拟按键、菜单绘制、作弊菜单 UI。
  - `GbaModels.kt`：菜单、按键、存档、作弊码模型。
- 这版不改变 v0.1.51/v0.1.52 的 GBA 金手指稳定方案。关闭作弊码仍然是自动快存 + 重启当前游戏。

## v0.1.51

- 停止继续推进 native CheatManager，保留 v0.1.48 的稳定关闭方案。
- 作弊菜单增加说明：关闭作弊码需要重启当前游戏。
- 关闭/删除已启用作弊码时，自动执行一次快捷存档，然后冷重启当前 GBA 游戏。
- 说明当前方案是稳定过渡版：开启即时生效，关闭通过重启 core 确保失效。

## v0.1.48

- 修复 v0.1.47 关闭作弊码后游戏直接退出的问题。
- 冷重启请求改由主进程 MainActivity relay，避免新旧 InternalGbaActivity 在同一 :internal_gba 进程里被一起 kill。

## 结论

当前 GBA 金手指以 v0.1.56 作为稳定断点版本：保留自动快存 + 重启 + 快读的稳定关闭方案，同时修复作弊菜单文字重叠、拆分 GBA 代码结构，并优化多个作弊码同时启用的下发方式。如果后续不再继续投入 native CheatManager，可以直接把此版本作为正式方案；下一项进入 PSP。

## v0.1.52 - 模拟器代码目录整理

- 将 GBA 内置模拟器代码移动到 `app/src/main/java/com/bond/md3elauncher/emulator/gba/`。
- 将内置模拟器注册入口移动到 `emulator/InternalEmulators.kt`。
- 将 PSP ISO 读取器移动到 `emulator/psp/PspIsoReader.kt`，为后续 PSP 内置模拟器做目录预留。
- 新增 `emulator/fc/`、`emulator/nse/` 预留目录，方便后续按平台拆分学习和维护。
- 本次仅做代码目录整理，不改变 v0.1.51 的 GBA 作弊码稳定行为。


## v0.1.57 - FC/NES external emulator phase 1

- GBA 作弊码稳定逻辑不变。
- 新增 FC/NES 平台，作为外部模拟器启动入口。
- 支持扫描 `.nes/.fds/.unf/.unif/.zip/.7z`。
- 新增 `emulator/fc/FcExternalEmulatorProfiles.kt`。
- 支持推荐 Nes.emu / Nostalgia.NES / John NESS / RetroArch。
- 暂不内置 FC/NES core，后续如需要再接 FCEUmm / Nestopia。

## v0.1.83
- 修复 v0.1.82 中 GBA Activity 调用 i18n `tr(...)` 辅助函数但未定义导致的 Kotlin 编译失败。
- 保持 v0.1.82 的 GBA/FC 模拟器文本 JSON 化改动不变。

## v0.1.85
- GBA / GB/GBC normal exit now closes the `:internal_gba` process after returning to launcher, so language changes from Settings do not keep stale emulator UI text.
