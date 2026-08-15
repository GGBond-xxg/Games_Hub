# GameHub

[![Release](https://img.shields.io/github/v/release/GGBond-xxg/Games_Hub?display_name=tag)](https://github.com/GGBond-xxg/Games_Hub/releases/latest)
[![Android](https://img.shields.io/badge/Android-6.0%2B-3DDC84?logo=android&logoColor=white)](https://github.com/GGBond-xxg/Games_Hub/releases/latest)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

GameHub 是一款面向 Android 横屏设备的开源游戏启动器。它可以统一整理本地游戏、安卓 App、收藏与封面，并让支持的平台在内置模拟器和第三方模拟器之间自由切换。

当前正式版本：`v1.0.1`

Android 包名：`com.bond.md3elauncher`

> GameHub 不提供 ROM、BIOS、固件、密钥或其他受版权保护的游戏内容。请只使用你有权使用的文件。

## 下载与安装

- [下载最新版 GameHub APK](https://github.com/GGBond-xxg/Games_Hub/releases/latest)
- 系统要求：Android 6.0（API 23）或更高版本
- `GameHub-v1.0.1-arm64.apk`：推荐，适用于绝大多数现代 Android 手机、平板和掌机。
- `GameHub-v1.0.1-arm32.apk`：仅用于较旧的 32 位 ARM 设备。

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

欢迎提交 Issue 和 Pull Request。修改前请阅读 [AGENTS.md](AGENTS.md) 中的项目约束，特别注意：

- 不要提交 ROM、BIOS、固件、密钥或商业游戏素材。
- 不要改变 `applicationId`，以免破坏升级和本地数据兼容性。
- 新增可见文本时同步更新 English、简体中文和繁體中文。
- 保留右侧预览、最多四列宫格以及现有手柄操作。
