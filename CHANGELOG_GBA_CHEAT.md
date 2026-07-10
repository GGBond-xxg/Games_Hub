# GBA Cheat Changelog

## v0.1.51

- 停止继续推进 native CheatManager，保留 v0.1.48 的稳定关闭方案。
- 作弊菜单增加说明：关闭作弊码需要重启当前游戏。
- 关闭/删除已启用作弊码时，自动执行一次快捷存档，然后冷重启当前 GBA 游戏。
- 说明当前方案是稳定过渡版：开启即时生效，关闭通过重启 core 确保失效。

## v0.1.48

- 修复 v0.1.47 关闭作弊码后游戏直接退出的问题。
- 冷重启请求改由主进程 MainActivity relay，避免新旧 InternalGbaActivity 在同一 :internal_gba 进程里被一起 kill。

## 结论

当前 GBA 金手指以 v0.1.51 作为稳定断点版本。如果后续不再继续投入 native CheatManager，可以直接把此版本作为正式方案；下一项进入 PSP。

## v0.1.52 - 模拟器代码目录整理

- 将 GBA 内置模拟器代码移动到 `app/src/main/java/com/bond/md3elauncher/emulator/gba/`。
- 将内置模拟器注册入口移动到 `emulator/InternalEmulators.kt`。
- 将 PSP ISO 读取器移动到 `emulator/psp/PspIsoReader.kt`，为后续 PSP 内置模拟器做目录预留。
- 新增 `emulator/fc/`、`emulator/nse/` 预留目录，方便后续按平台拆分学习和维护。
- 本次仅做代码目录整理，不改变 v0.1.51 的 GBA 作弊码稳定行为。

