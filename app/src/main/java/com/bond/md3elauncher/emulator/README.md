# Emulator Code Area

这里是模拟器相关代码的统一入口。后续新增 PSP、FC、NSE/NS 等内置模拟器时，尽量都放在这个目录下，避免和启动器 UI、数据存储、系统应用扫描代码混在一起。

当前结构：

```text
emulator/
├── InternalEmulators.kt          # 内置模拟器注册/识别入口
├── gba/
│   ├── README.md                 # GBA / GB / GBC 目录说明
│   ├── InternalGbaActivity.kt    # GBA / GB / GBC Activity 生命周期、ROM 启动、存档、作弊码流程
│   ├── GbaTouchControlsView.kt   # GBA 虚拟按键和菜单绘制
│   ├── GbaModels.kt              # GBA 菜单、按键、存档、作弊码模型
│   └── GbaNativeCheatBridge.kt   # 预留的 GBA native CheatManager 桥接入口
├── psp/
│   └── PspIsoReader.kt           # PSP ISO 元数据读取，后续 PSP 内置模拟器放这里
├── fc/
│   ├── README.md                 # FC/NES 目录说明
│   ├── InternalFcActivity.kt     # FC/NES 内置 Activity、ROM 启动、快存快读
│   ├── FcTouchControlsView.kt    # FC/NES 虚拟按键和菜单绘制
│   ├── FcModels.kt               # FC/NES 存档、触控按钮、虚拟键模型
│   └── FcExternalEmulatorProfiles.kt # FC/NES 外部模拟器关键词和 RetroArch core 候选
└── nse/
    └── README.md                 # NSE/NS 模拟器预留目录
```

约定：

- 一个模拟器一个子目录。
- Activity、核心控制、按键映射、存档、作弊码、native bridge 都放到对应模拟器目录。
- 公共注册入口放在 `emulator/InternalEmulators.kt`。
- 启动器首页、设置页、数据层只调用 emulator 目录暴露出来的入口，不直接写模拟器细节。

GBA / GB/GBC 拆分说明：

- `InternalGbaActivity.kt`：只保留 Activity 主流程、ROM 启动、存档/读档、作弊码执行流程。
- `GbaTouchControlsView.kt`：负责屏幕按键、菜单面板、作弊菜单列表和触摸/手柄菜单交互。
- `GbaModels.kt`：负责 GBA 内部用到的数据类、枚举和虚拟键常量。

FC/NES 说明：

- v0.1.57-v0.1.63：FC/NES 主要走外部模拟器方案。
- v0.1.64 起：加入内置 FC/NES Phase 1，使用 FCEUmm libretro core。
- v0.1.65 起：GBA / FC/NES 共用“手柄操作”快捷键配置；FC/NES 内置启动改用 ASCII 缓存 ROM 路径。
- 外部模拟器仍保留，Nes.emu 已测试可一键直启，RetroArch 继续作为备用方案。

后续 PSP、NSE/NS 也按这个规则拆分，避免单个模拟器文件越来越大。

## 内置模拟器公共 UI 规范（v0.1.74 起）

后续所有内置模拟器必须复用 `emulator/common/` 下的公共 UI 规范。

```text
emulator/common/CommonEmulatorUiSpec.kt
emulator/common/CommonTouchLayout.kt
emulator/common/CommonEmulatorHost.kt
```

GBA 是当前视觉基准。FC/NES 已从 v0.1.74 开始复用 GBA 标准虚拟按键排布。新增内置模拟器时，不要复制一份新的菜单和按键样式，必须先接入公共模块，再实现自己的 core 操作。

统一主菜单：

```text
存档
虚拟按键设置
作弊
重置
重启游戏
退出游戏
```

统一存档：

```text
存档 1-5
快捷存档
A 存档 / Y 读档 / X 删除 / B 返回
```

统一虚拟按键：

```text
GBA 标准排布：方向键 + Y/X/B/A + SELECT/START + L/R + 菜单/快存/快读/快进/退出
```

区别只允许存在于“功能实现层”，例如 GBA 有作弊码实现，FC/NES 暂时只保留作弊入口；不同模拟器不应有不同风格的内置菜单。


## v0.1.76 GB/GBC 接入规范

GB/GBC 不新增单独 Activity，先复用 `emulator/gba/InternalGbaActivity.kt` 和 mGBA libretro core。

约定：

```text
GBA 平台：.gba / .zip / .7z
GB/GBC 平台：.gb / .gbc / .sgb / .zip / .7z
内部 core：都走 libmgba_libretro_android.so
UI：都走 GBA 标准按键排布和公共内置菜单规范
```

不要把 `.gb/.gbc` 放到 FC/NES 平台处理。GB/GBC 与 GBA 的区别主要是启动器平台分类和扫描扩展名，模拟器执行层共用 mGBA。

## v0.1.77 编辑页与封面规范

长按游戏进入编辑显示信息页后，右侧操作区必须可滚动，避免按钮或提示溢出。

封面建议：

```text
比例：竖版 3:4
推荐尺寸：600×800 px
格式：PNG / JPG
显示方式：ContentScale.Fit，自动适配，不强制裁剪
```

手柄操作说明文案固定为：`设置内置模拟器通用快捷键，支持1~3键组合。`
