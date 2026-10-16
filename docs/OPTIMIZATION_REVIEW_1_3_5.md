# GameHub 1.3.5 优化与内置模拟器评估

检查日期：2026-10-16。安装包基线为本地正式签名 `build/release-v1.3.4/` 两个 APK。本文区分已做的安全清理与尚未实现的建议；没有进行 ROM 实玩或新增内核兼容性测试。

## 已做：移除未被运行路径使用的旧副本

删除以下四份历史 Kotlin 文件，合计约 3,370 行：根包 `InternalGbaActivity.kt`、根包 `GbaNativeCheatBridge.kt`、`system/InternalEmulators.kt`、`io/PspIsoReader.kt`。最后一份文件附带的旧 `PspIsoMetadata` 没有外部使用。

检查范围包括 Manifest、所有当前 Kotlin/Java 引用、反射、JNI、Gradle 原生构建与 keep 配置。活动启动入口显式导入 `emulator/` 下对应实现；旧 Activity 没有注册。`cpp/gba_cheat_bridge.cpp` 中确有旧根包 JNI 名称，但 Gradle 没有启用该 CMake 工程、APK 没有该桥接库、旧 Kotlin scaffold 也没有 external 声明或库加载器。保留未启用的 C++ scaffold，不把它误当当前金手指实现。

本次没有改活动模拟器按键处理、存档路径、持久化平台标识、外部模拟器 Intent 或原生核心内容。

## 已做：移除打包但未使用的三个 native 库

当前 FC/NES Activity 与启动前检查均固定选择 Nestopia；源代码注释明确拒绝历史 FCEUmm/Mesen 回退。只有外部 RetroArch 的候选名称提到 FCEUmm/Mesen，路径是所选 **RetroArch 包** 的 `/data/data/<package>/cores/`，不读取 GameHub 的 `nativeLibraryDir`。备份文件白名单不包含 `.so`。

两个基线 APK 共 22 个 ELF 库经 Android NDK `llvm-readelf --dynamic` 检查，没有任何库通过 `DT_NEEDED` 依赖下面三项；其他库内也没有嵌入这两个完整文件名。删除对象：

| ABI / 文件 | APK 中压缩字节数 | 原始字节数 |
|---|---:|---:|
| arm64-v8a / libfceumm_libretro_android.so | 486,092 | 4,250,800 |
| arm64-v8a / libmesen_libretro_android.so | 1,081,795 | 3,797,448 |
| armeabi-v7a / libfceumm_libretro_android.so | 368,638 | 2,161,932 |

仅 native 压缩内容预计减少 arm64 **1,567,887 B（1.50 MiB）**、arm32 **368,638 B（0.35 MiB）**；最终签名 APK 的总大小还受 DEX、ZIP 元数据、版本文字影响，应以最终构建记录为准。源码中未打包的 x86/x86_64 FCEUmm 不在本次删除范围。

**既有 ARM32 限制**：1.3.4 APK 已经没有 ARM32 Nestopia，而 FC/NES 启动前检查要求 Nestopia，因此 ARM32 的内置 FC/NES 本来就不可用。此次删除未使用 FCEUmm 不改变该状态，也没有改为未经验证的替代核心。补齐 ARM32 Nestopia 应单独核实来源、许可证与真实设备兼容性；目前可使用外部 FC 模拟器。

## 安装包组成与后续建议（尚未实现）

下表是 ZIP 各项压缩字节数之和；不含 ZIP/签名开销，因此不会与 APK 文件长度完全相等。

| 1.3.4 基线 | arm64 | arm32 |
|---|---:|---:|
| APK 文件长度 | 33,313,346 | 29,397,227 |
| native 库 | 18,582,661 | 14,666,828 |
| DEX | 14,053,490 | 14,053,490 |
| Android 资源 | 549,372 | 549,372 |
| assets（含三语文本、法律说明） | 57,617 | 57,617 |

- **DEX 是下一项体积重点**：release 目前没有开启 R8，且依赖完整 Material Icons Extended。可先统计真实图标使用范围，再独立评估替换为本地所需矢量图；不要为体积盲目删除 UI 库。R8 必须另做 JNI、反射、独立模拟器进程及保存/加载回归，不作为这次安全清理的顺带改动。Android 官方说明：[优化应用](https://developer.android.com/topic/performance/app-optimization/enable-app-optimization)。
- **MAME 2003-Plus 是 native 最大项**：arm64 压缩 8,466,932 B、arm32 6,879,494 B。它是活动街机核心，不能删除冒充优化。未来可研究“完整包 / 仅启动器包”或签名校验的可选核心，但会涉及离线体验、更新、许可证和兼容性，不是简单删除文件。
- **保留 N64 GLES2/GLES3**：两版用于设备图形能力分支，不因库较大就删其一。保留现有符号和 JNI 打包方式，不对核心执行 strip。
- **性能先测再改**：图片已有尺寸限制、2 路解码与 16 MB 共享缓存，继续保持；优先测大型库首屏、滚动帧耗时、扫描 IO、网络取消耗时及后台动画暂停。背景动画在绘制阶段读取状态，避免每帧触发整页重组。建议下一轮建立 release Macrobenchmark/Baseline Profile，而非只以 debug 手感判断。官方依据：[Macrobenchmark](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview)、[Baseline Profiles](https://developer.android.com/topic/performance/baselineprofiles/overview)。这些属于下一步建议，本次没有宣称已量得 FPS 或内存改善。

## 可以新增哪些内置平台（可行性评估，尚未实现）

下表的难度是依据当前 GameHub 主机、存储和输入架构作出的工程判断；核心支持某平台，不代表 GameHub 已支持。

| 优先级 / 平台 | 核心、ABI 与依赖 | 主要集成工作 |
|---|---|---|
| 1：Master System / Game Gear / SG-1000 | 已有 Genesis Plus GX，两种发布 ARM ABI 都已打包；SMS/GG BIOS 可选，非商业许可 | 新平台 ID、扩展名与刮削映射、平台独立存档目录、2 键触控和暂停键、核心选项与真实游戏验证。通常不必增加新 `.so`，是最低体积成本的路线。 |
| 2：PC Engine 卡带 | 新增 Beetle PCE FAST，GPLv2；Android 构建配方存在，具体 ARM32/64 发布产物与 API/页大小仍须核验 | `.pce` 扫描、手柄映射、保存与快进测试；卡带先行。CD 另需用户提供 System Card BIOS、CUE/多轨文件处理。此核心不含 SuperGrafx。 |
| 2：32X | 需要新增 PicoDrive；已有 Genesis Plus GX **不支持 32X**。上游为非商业许可，必须独立核对候选构建的 ARM32/64 ABI | 6 键布局、核心选项、存档与性能测试。不能仅往 MD 的扩展名加 `.32x` 就认为完成。 |
| 3：Mega/Sega CD | 现有 Genesis Plus GX 可复用，但需要用户提供地区 BIOS | BIOS 导入/校验、SAF 多文件 CUE 解析、换盘与额外 BRAM 存档备份；比 SMS/GG 更复杂，不能只传入一份缓存 ROM。 |
| 3：PSP | PPSSPP libretro，GPLv2；无需原机 BIOS，但需要与内核匹配的 PPSSPP 辅助 assets | 新核心双 ABI 验证、GLES 上下文与性能、摇杆、内存卡文件目录、存档与选项。当前 PSP ISO 解析可复用，外部 PPSSPP 入口继续保留。 |
| 4：NDS | melonDS DS，GPLv3；DS BIOS/firmware 有内置替代，DSi 模式需用户文件；两个 Android ABI 必须针对选定版本验证 | 双屏布局、触屏坐标/触笔、麦克风接口、屏幕切换手柄键、独立存储。DSi 及 homebrew 即时存档存在上游限制，不能保证通用“5 槽 + 快存”；应先限定经过验证的 DS 模式。 |
| 4：Dreamcast | Flycast，GPLv2；上游有 Android 构建路线，具体双 ABI 与 GLES 功能仍需实测；可选用户 BIOS/HLE | 模拟扳机、VMU/NVRAM 文件保存与备份、光盘/换盘、图形能力和状态兼容性。优先普通 DC，NAOMI/Atomiswave 不自动一起承诺。 |

优先建议是 **SMS / Game Gear / SG-1000 → PCE 卡带或 32X → PSP → DS / Dreamcast**。PS2、3DS、GameCube/Wii、Switch 应继续使用现有外部模拟器，直到单独确认可维护内核、硬件/ABI、输入和性能门槛。

官方资料（2026-10-16 查阅）：

- [Genesis Plus GX 平台、BIOS、许可证](https://docs.libretro.com/library/genesis_plus_gx/)
- [PicoDrive 平台与许可证](https://docs.libretro.com/library/picodrive/)
- [Beetle PCE FAST 平台、BIOS、许可证](https://docs.libretro.com/library/beetle_pce_fast/)
- [PPSSPP libretro 与 assets](https://docs.libretro.com/library/ppsspp/)
- [melonDS DS 固件与接口](https://docs.libretro.com/library/melonds_ds/)、[上游即时存档/麦克风限制](https://github.com/JesseTG/melonds-ds/blob/main/README.md)
- [Flycast 平台、BIOS、存储](https://docs.libretro.com/library/flycast/)
- [Libretro 官方 Android 构建配方](https://github.com/libretro/libretro-super/blob/master/recipes/android/cores-android)

未下载或添加 ROM、原机 BIOS、固件、密钥或商业素材；本文不是新增平台已可运行的声明。
