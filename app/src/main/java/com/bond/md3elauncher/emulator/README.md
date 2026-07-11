# Emulator Code Area

这里是模拟器相关代码的统一入口。后续新增 PSP、FC、NSE/NS 等内置模拟器时，尽量都放在这个目录下，避免和启动器 UI、数据存储、系统应用扫描代码混在一起。

当前结构：

```text
emulator/
├── InternalEmulators.kt          # 内置模拟器注册/识别入口
├── gba/
│   ├── README.md                 # GBA 目录说明
│   ├── InternalGbaActivity.kt    # GBA Activity 生命周期、ROM 启动、存档、作弊码流程
│   ├── GbaTouchControlsView.kt   # GBA 虚拟按键和菜单绘制
│   ├── GbaModels.kt              # GBA 菜单、按键、存档、作弊码模型
│   └── GbaNativeCheatBridge.kt   # 预留的 GBA native CheatManager 桥接入口
├── psp/
│   └── PspIsoReader.kt           # PSP ISO 元数据读取，后续 PSP 内置模拟器放这里
├── fc/
│   ├── README.md                 # FC/NES 目录说明
│   └── FcExternalEmulatorProfiles.kt # FC/NES 外部模拟器关键词和 RetroArch core 候选
└── nse/
    └── README.md                 # NSE/NS 模拟器预留目录
```

约定：

- 一个模拟器一个子目录。
- Activity、核心控制、按键映射、存档、作弊码、native bridge 都放到对应模拟器目录。
- 公共注册入口放在 `emulator/InternalEmulators.kt`。
- 启动器首页、设置页、数据层只调用 emulator 目录暴露出来的入口，不直接写模拟器细节。


GBA 拆分说明：

- `InternalGbaActivity.kt`：只保留 Activity 主流程、ROM 启动、存档/读档、作弊码执行流程。
- `GbaTouchControlsView.kt`：负责屏幕按键、菜单面板、作弊菜单列表和触摸/手柄菜单交互。
- `GbaModels.kt`：负责 GBA 内部用到的数据类、枚举和虚拟键常量。

FC/NES 当前先走外部模拟器方案，后续如要内置 FCEUmm / Nestopia core，也继续放在 `emulator/fc/` 目录。

后续 PSP、FC/NES、NSE/NS 也按这个规则拆分，避免单个模拟器文件越来越大。
