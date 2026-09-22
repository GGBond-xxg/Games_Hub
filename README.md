# GameHub

[![Release](https://img.shields.io/github/v/release/GGBond-xxg/Games_Hub?display_name=tag)](https://github.com/GGBond-xxg/Games_Hub/releases/latest)
[![Android](https://img.shields.io/badge/Android-6.0%2B-3DDC84?logo=android&logoColor=white)](https://github.com/GGBond-xxg/Games_Hub/releases/latest)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

GameHub 是一款面向 Android 横屏设备的开源游戏启动器。它可以统一整理本地游戏、安卓 App、收藏与封面，并让支持的平台在内置模拟器和第三方模拟器之间自由切换。

为手机、平板和安卓掌机打造一个用手柄也能轻松操作的游戏入口：选好平台、整理游戏、按下 `A` 开玩。

当前正式版本：`v1.1.1`

Android 包名：`com.bond.md3elauncher`

> GameHub 不提供 ROM、BIOS、固件、密钥或其他受版权保护的游戏内容。请只使用你有权使用的文件。

## 下载与安装

- [下载最新版 GameHub APK](https://github.com/GGBond-xxg/Games_Hub/releases/latest)
- 系统要求：Android 6.0（API 23）或更高版本
- `GameHub-v1.1.1-arm64.apk`：推荐，适用于绝大多数现代 Android 手机、平板和掌机。
- `GameHub-v1.1.1-arm32.apk`：仅用于较旧的 32 位 ARM 设备。

从早期 Debug 测试包升级到 `v1.0.0` 时，如果系统提示签名不一致，需要先卸载测试包再安装正式版。`v1.0.0` 之后使用同一发布签名，可直接覆盖升级。

## 主要功能

- 横屏 Material Design 3 启动器界面。
- 平台分类、收藏、搜索和独立排序。
- 列表与 1～4 列宫格布局，始终保留右侧大图预览。
- 预览图与宫格图独立设置，并兼容旧版单图数据。
- 扫描本地 ROM 与已安装安卓 App。
- 内置模拟器与第三方模拟器自由切换。
- 内置模拟器共享触控按键、实体手柄快捷键和存档菜单。
- 5 个普通即时存档槽及 1 个快捷存档。
- English、简体中文、繁體中文三语界面。

### v1.1.1 更新

- 统一设置页分组、标题、卡片与间距，外观和显示设置分开呈现。
- 备份、关于我们和当前版本入口移到设置最底部，等宽排列。
- 宽屏左右边距并排显示；语言和方向选项在空间不足时自动换行。
- 开关支持整行点击，扫描期间禁用重复扫描按钮。
- 调整封面配置标签字号，并为输入框下方说明增加留白。

### v1.1.0 新增

- **最近游玩**：汇集最近启动的游戏和安卓 App，支持搜索、收藏、编辑、列表 / 宫格和右侧预览。默认按最近启动排序；可用 `L3` / `R3` 调整，下一次启动后恢复时间顺序。
- **扫描保护**：目录无法读取、权限失效或扫描不完整时保留原游戏库，并提示重新选择目录。全平台扫描全部成功后才更新列表。
- **封面加载优化**：后台解码、限制图片尺寸并共享内存缓存，减少界面线程上的图片读取工作。
- **备份与恢复**：在设置中导出或恢复游戏库、收藏、排序、自定义封面、设置，以及内置模拟器的普通存档、快捷存档和电池存档。
- **关于我们**：项目介绍、离线许可与第三方说明、GitHub 反馈入口，以及可复制的自愿赞助地址。

- **应用内更新**：点击设置中的当前版本号，检查 GitHub 最新正式版；自动匹配 ARM64 / ARM32 安装包，校验 SHA-256、包名、发布签名和递增版本后打开系统安装器。
- **游戏库容错**：逐条读取游戏记录，跳过坏记录；整份数据损坏时尝试恢复上一份有效列表。
- **启动检查**：提前提示文件授权失效、文件不可读、缺失核心、模拟器未安装及内置模拟器不支持的格式，并提供平台设置入口。
- **更多封面来源**：Libretro 覆盖 GB/GBC、FC/NES、SFC/SNES、MD、PS1、N64、街机等平台，接入 ScreenScraper 搜索。

## 检查更新

进入 **设置 → 当前版本**，点击版本号主动检查更新。发现新版本后选择“下载更新”；首次使用时按系统提示允许 GameHub 安装应用，返回后确认安装。更新保留已有游戏库和配置。

更新检查与下载需要访问 GitHub。网络失败可重试或打开发布页面手动下载；取消更新窗口会停止本次下载。安装包不匹配、校验失败或签名不同会停止安装。不会在后台自动检查或静默安装。

## 封面来源配置

在 **设置 → 封面刮削** 配置来源，保存后长按游戏进入编辑，选择联网搜索封面。Libretro 无需账号，按游戏文件名称匹配，找不到时可尝试英文标准名称。

ScreenScraper 需要你自己的开发者 ID 和开发者密码，普通用户账号可选；仅填写普通账号无法调用接口。接口要求见 [ScreenScraper 官方 API 文档](https://www.screenscraper.fr/webapi2.php?alpha=0&numpage=0)。不内置或共享第三方凭据，所有来源凭据只保存在本机且排除在导出备份之外。未配置或请求失败会在搜索报告中说明。

## 快速开始

1. 安装适合设备架构的 APK，打开 GameHub。
2. 进入 **设置 → 平台管理**，选择平台并授权 ROM 文件夹。
3. 选择内置或已安装的外部模拟器，扫描游戏。
4. 返回游戏列表，按 `A` 启动、按 `B` 收藏；长按项目可编辑名称与图片。

游戏未出现时，先检查文件格式与目录授权。扫描失败不会清空原列表，可在平台设置中重新选择文件夹。内置核心的可用性与设备架构有关，详见[已知限制](docs/KNOWN_ISSUES.md)。

## 备份与换机

在 **设置 → 备份与恢复** 中选择导出位置，生成 `GameHub-backup.zip`。操作前请先从内置模拟器菜单正常退出游戏，确保最新存档已经写入。

- 备份不包含 ROM、BIOS、固件、外部模拟器存档或封面服务账号密码。
- 恢复会替换启动器设置、覆盖同名封面和存档，保留其他存档文件。需要保留当前配置时，请先导出一份备份。
- 恢复前会校验备份格式、文件路径与大小；单个文件最大 64 MB，整个备份解压后最大 512 MB。
- 换机后需重新安装外部模拟器，并在平台设置中重新授权 ROM 文件夹。目录 URI 变化时，收藏、封面等关联可能需要重新设置。
- 即时存档能否跨核心版本或设备使用取决于模拟器核心，建议同时保留游戏内的电池存档。

## 平台支持

| 平台 | 内置模拟器 | 外部模拟器 | 常用文件格式 |
|---|---|---|---|
| GBA | mGBA | My Boy!、Pizza Boy、RetroArch | `.gba`、`.zip`、`.7z` |
| GB/GBC | mGBA | My OldBoy!、RetroArch | `.gb`、`.gbc`、`.sgb`、`.zip`、`.7z` |
| FC/NES | Nestopia | Nes.emu、Nostalgia.NES、RetroArch | `.nes`、`.fds`、`.unf`、`.unif`、`.zip`、`.7z` |
| SFC/SNES | Snes9x | Snes9x EX+、RetroArch | `.sfc`、`.smc`、`.swc`、`.fig`、`.zip`、`.7z` |
| MD/Genesis | Genesis Plus GX | MD.emu、RetroArch | `.md`、`.gen`、`.smd`、`.bin`、`.zip`、`.7z` |
| PS1 | PCSX-ReARMed（HLE BIOS） | DuckStation、ePSXe、FPse、RetroArch | `.chd`、`.pbp`、`.iso`、`.bin` |
| N64 | Mupen64Plus-Next | M64Plus FZ、Mupen64Plus、RetroArch | `.z64`、`.v64`、`.n64`、`.bin`、`.zip`、`.7z` |
| 街机 | MAME 2003-Plus | MAME4droid、RetroArch、FinalBurn | `.zip` |
| PSP | — | PPSSPP、RetroArch | `.iso`、`.cso`、`.pbp`、`.chd` |
| Switch | — | 外部模拟器 | `.nsp`、`.xci`、`.nsz`、`.nro` |

### ROM 兼容性提示

- N64 会依据文件头识别真实字节序，并在启动内置核心前自动标准化，因此兼容扩展名不准确的有效 ROM 以及多数正确制作的汉化/修复版本。
- 街机建议使用与 MAME 2003-Plus 匹配的 Full Non-Merged ROM Set，并保持游戏 ZIP 文件及内部名称不变。
- `.7z` 可以被游戏库扫描，但当前内置模拟器不直接解压运行。
- PS1 多轨光盘应保留原始 `.cue` 与全部轨道文件；当前内置路径更适合单文件 `.chd`、`.pbp`、`.iso` 或 `.bin`。
- 兼容性仍会受到 ROM 完整性、补丁质量、父 ROM、BIOS、CHD 和具体设备 GPU 驱动影响。

## 操作方式

启动器保留以下手柄操作：

| 按键 | 功能 |
|---|---|
| `A` | 启动游戏或确认 |
| `B` | 收藏、添加或返回 |
| `L3` | 当前列表项目上移 |
| `R3` | 当前列表项目下移 |

内置模拟器支持触屏操作，也支持实体手柄和可配置快捷键。重置、重新开始和退出是相互独立的操作。

## 从源码构建

环境要求：

- JDK 17+
- Android SDK 36
- Windows、Linux 或 macOS

Debug 构建：

```powershell
.\gradlew.bat clean assembleDebug
```

Linux / macOS：

```bash
./gradlew clean assembleDebug
```

正式构建需要在项目根目录创建：

```text
.release-signing/keystore.properties
```

内容格式：

```properties
storeFile=/absolute/path/to/gamehub-release.jks
storePassword=your-store-password
keyAlias=gamehub
keyPassword=your-key-password
```

然后运行：

```powershell
.\gradlew.bat clean testDebugUnitTest assembleRelease
```

构建会输出 ARM64 和 ARM32 两个签名 APK。源码中的 x86/x86_64 核心仍可用于开发调试，但正式安装包不再携带不完整的 x86 模拟器组合。

发布签名文件和密码已被 `.gitignore` 排除，切勿提交到 GitHub。请妥善备份签名文件；丢失后将无法为已安装用户提供可覆盖升级的安装包。

## 项目结构

```text
app/src/main/java/com/bond/md3elauncher/
├── MainActivity.kt
├── data/                # 平台模型与本地设置
├── emulator/
│   ├── common/          # 内置模拟器公共菜单与触控布局
│   ├── gba/             # GBA / GB / GBC
│   ├── fc/              # FC / NES / SFC
│   ├── md/              # MD / Genesis
│   ├── ps1/             # PlayStation
│   ├── n64/             # Nintendo 64
│   └── arcade/          # MAME 2003-Plus
├── i18n/                # 三语 JSON 文本
├── io/                  # ROM 扫描
├── system/              # 外部模拟器与安卓 App
└── ui/                  # Compose / Material 3 界面
```

更多开发资料：

- [架构说明](docs/ARCHITECTURE.md)
- [更新记录](docs/CHANGELOG.md)
- [已知限制](docs/KNOWN_ISSUES.md)
- [国际化规范](docs/I18N.md)
- [第三方组件说明](THIRD_PARTY_NOTICES.md)
- [免责声明](DISCLAIMER.md)

## 许可证与责任

GameHub 自有代码采用 [MIT License](LICENSE)。LibretroDroid、各模拟器核心及其他第三方组件继续使用各自许可证，详见 [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)。

MAME 2003-Plus 等组件包含独立的非商业许可限制。MIT 许可证和项目免责声明不会改变第三方组件的许可条件。任何人修改、再分发或使用本项目时，都应自行检查第三方授权、游戏内容版权和目标分发渠道政策。

## 参与贡献

GameHub 源于一个让游戏体验更顺手的想法。项目创意、功能方向和体验取舍由作者主导，主要代码与文档在 GPT 的协助下完成，并通过持续测试与反馈不断完善。也感谢模拟器核心、依赖库及所有开源贡献者，让这个想法能够落地。

欢迎提交 Issue 和 Pull Request。修改前请阅读 [AGENTS.md](AGENTS.md) 中的项目约束，特别注意：

- 不要提交 ROM、BIOS、固件、密钥或商业游戏素材。
- 不要改变 `applicationId`，以免破坏升级和本地数据兼容性。
- 新增可见文本时同步更新 English、简体中文和繁體中文。
- 保留右侧预览、最多四列宫格以及现有手柄操作。

## 支持项目

如果 GameHub 对你有帮助，欢迎通过反馈问题、分享使用体验、贡献代码或自愿赞助支持项目持续维护。所有功能均可免费使用，赞助不解锁额外功能。

请使用地址对应的网络，并在转账前核对完整地址。感谢你的支持！

| 网络 | 赞助地址 |
|---|---|
| Solana（SOL） | `GseMb4yCgfhyMvkA7jP6QhMe4e5nMPnxgJqqrA8Aq7eJ` |
| Ethereum（ETH / ERC-20） | `0xcB2f6fc5eF905e89cDeE7F2eB59faD9A93043324` |
| TON | `UQATTF8wVv_Q8x42OYyDOUcM1Ti0HA-VdZ0b5zUd78_lEYqj` |
| TRON（TRX / TRC-20） | `TXDFyQKRSLt6dmbs3tcJbEJbcHsokbgKSn` |
| Sui（SUI） | `0xd61db83d28fc0da34e55b9488d3927fc5b6515cc96c6109c0f8ed451980af4bb` |
| Bitcoin（BTC） | `1BhMBUVLySJg3qNgFNxYPFMGbcd4KFxkrK` |
| Dogecoin（DOGE） | `DEJ6MMqAjX55YfXXtFQVeYrCbadntYwZCs` |
