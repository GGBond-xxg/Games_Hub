# Emulator Code Area

这里是模拟器相关代码的统一入口。后续新增 PSP、FC、NSE/NS 等内置模拟器时，尽量都放在这个目录下，避免和启动器 UI、数据存储、系统应用扫描代码混在一起。

当前结构：

```text
emulator/
├── InternalEmulators.kt          # 内置模拟器注册/识别入口
├── gba/
│   ├── InternalGbaActivity.kt    # GBA 内置模拟器主界面、菜单、按键、存档、作弊码
│   └── GbaNativeCheatBridge.kt   # 预留的 GBA native CheatManager 桥接入口
├── psp/
│   └── PspIsoReader.kt           # PSP ISO 元数据读取，后续 PSP 内置模拟器放这里
├── fc/
│   └── README.md                 # FC 模拟器预留目录
└── nse/
    └── README.md                 # NSE/NS 模拟器预留目录
```

约定：

- 一个模拟器一个子目录。
- Activity、核心控制、按键映射、存档、作弊码、native bridge 都放到对应模拟器目录。
- 公共注册入口放在 `emulator/InternalEmulators.kt`。
- 启动器首页、设置页、数据层只调用 emulator 目录暴露出来的入口，不直接写模拟器细节。
