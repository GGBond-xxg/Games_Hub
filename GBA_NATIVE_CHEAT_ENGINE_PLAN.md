# GBA Native Cheat Engine Plan / v0.1.51

## 当前决策

暂时停止继续推进 native CheatManager。当前项目先采用 v0.1.51 稳定方案：

- 开启作弊码：游戏内即时下发。
- 关闭/删除已启用作弊码：自动快捷存档，然后冷重启当前 GBA 游戏，重启后自动快速读档。
- 重启后恢复开启作弊前的 clean state，确保穿墙这类代码失效。

如果后续 native CheatManager 做不稳定或投入成本过高，就以 v0.1.51 作为 GBA 金手指正式版，并转入 PSP 模块。

## 为什么需要重启

LeafGreen 的穿墙码 `509197D3 542975F4 / 78DA95DF 44018CB4` 可以开启生效，但在当前 LibretroDroid + mGBA libretro 外层方案里，关闭后无法通过 `resetCheat()`、软重置、恢复 clean state 稳定撤销。

这类 GameShark / CodeBreaker 码会在当前 native core 内留下运行期 patch。要彻底清掉，最稳定的方法是重新创建 GBA core。

## v0.1.51 稳定方案

1. 关闭/删除已启用作弊码。
2. 自动保存一次快捷存档，给用户保留当前进度备份。
3. 将冷重启请求交给主进程 `MainActivity`。
4. 旧 `:internal_gba` 进程退出。
5. 主进程延迟重新启动新的 `InternalGbaActivity`。
6. 新 GBA core 启动后恢复 clean state，并且不重新应用已关闭的作弊码。

## 后续真正 My Boy! 方案

后续如果继续做 native CheatManager，目标仍然是：

- 在 native core 内解析 GameShark / CodeBreaker。
- RAM 写入类每帧写入。
- ROM / 指令 patch 类保存原始字节，关闭时恢复。
- Kotlin 侧 A 开关只更新 native CheatManager，不再重启 core。

当前版本先不继续投入该方案，优先进入 PSP。
