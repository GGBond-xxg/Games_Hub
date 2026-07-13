# GBA 内置模拟器代码说明

这个目录只放 GBA 内置模拟器相关代码，外部 GBA 模拟器 App 的启动逻辑仍然放在 `system/ExternalLauncher.kt`。

当前文件划分：

```text
gba/
├── InternalGbaActivity.kt     # Activity 生命周期、ROM 启动、存档/读档、作弊码执行流程
├── GbaTouchControlsView.kt    # 虚拟按键、菜单绘制、作弊菜单 UI、触摸/手柄菜单交互
├── GbaModels.kt               # 菜单枚举、按键模型、存档模型、作弊码模型、虚拟键常量
└── GbaNativeCheatBridge.kt    # native CheatManager 预留入口
```

维护约定：

- 和界面绘制有关的内容，优先放到 `GbaTouchControlsView.kt`。
- 和 GBA 启动、存档、读档、作弊码执行流程有关的内容，放到 `InternalGbaActivity.kt`。
- 枚举、数据类、常量，放到 `GbaModels.kt`。
- native bridge 或后续 mGBA 原生能力，放到 `GbaNativeCheatBridge.kt` 或同目录新增 native 相关文件。

当前 GBA 金手指关闭策略：

```text
关闭作弊码
↓
自动快速存档
↓
重启当前 GBA 游戏
↓
自动快速读档
```

如果快速读档失败，会回退读取开启作弊前的 clean state，避免穿墙这类作弊码继续残留。
这是当前稳定方案。无感关闭需要后续继续实现 native CheatManager。

## 多作弊码下发策略

v0.1.56 起，一个自定义作弊码对应一个 libretro cheat slot。

```text
slot 0 = 第一个已启用作弊码
slot 1 = 第二个已启用作弊码
slot 2 = 第三个已启用作弊码
```

不要把多个不同作弊码全部拼进同一个 `setCheat(0, ...)`，否则可能出现前一个作弊码正常、后一个作弊码不生效的问题。

单个作弊码内部的多行代码仍然使用 `+` 合并后下发。

## v0.1.63 补充

基于 v0.1.60 的 GBA 金手指稳定逻辑回退，不再使用 v0.1.61/v0.1.62 的排序/组合兼容尝试。

在 GBA 作弊码菜单增加提示：穿墙和闪光同时开启时，部分 ROM / mGBA libretro 组合可能互相影响导致其中一个失效，建议最好只开一个。该提示仅提醒用户，不阻挡同时开启。


## v0.1.76 GB/GBC 说明

`InternalGbaActivity` 现在同时作为 GBA 与 GB/GBC 的内置启动 Activity。

支持直读：

```text
.gba / .agb / .gb / .gbc / .sgb / .bin
```

普通 `.zip` 内置启动时会尝试解出上述扩展名。`.7z` 当前只扫描，不在内置启动时解压。

GB/GBC 平台必须在启动器里作为独立平台展示，避免用户把 `.gb/.gbc` 误认为 FC/NES ROM。
