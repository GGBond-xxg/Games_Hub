# v1.3.5：取消图片请求、波浪背景与安全清理

## 修改范围

- 预览图、宫格图手动搜索均默认“封面”，可主动选“截图”。搜索和下载期间，类型与查询操作保持可用；切换会取消旧请求，每次请求独立保存诊断，只有最新请求可发布结果。取消下载产生的临时文件会清理。两种图片仍分别保存，批量补全不把封面当截图。
- Glass / MIUIX 背景采用三层主题色渐变曲线、10/15/20 秒单程往返运动，基于 Beacon 默认背景行为独立实现。Path 和渐变缓存，动画值仅在绘制阶段读取；保留关闭开关、系统动画缩放与后台停止机制。M3 背景不改。
- 删除经运行入口、反射、JNI、Gradle 与 native 依赖核实无用的四份 Kotlin 旧副本、三个已打包的 FCEUmm/Mesen 库。详见 [清理及后续评估](OPTIMIZATION_REVIEW_1_3_5.md)。

## lint：37 → 27

完整 `lintRelease` 为 **0 错误 / 27 警告**，没有新增忽略规则或 baseline。删除旧 GBA 副本消除 7 项 UseKtx、2 项 SetTextI18n、1 项 ClickableViewAccessibility。

| 保留项目 | 数量 | 不机械修复的原因 |
|---|---:|---|
| UseKtx | 1 | 恢复备份需要检查 `commit()` 的 Boolean，KTX edit 返回 Unit。 |
| ClickableViewAccessibility | 2 | 活动模拟器多点触控与拖动需要独立无障碍设计，不能把每次抬手视为 click。 |
| DiscouragedApi | 7 | 模拟器横屏声明涉及 Android 16+ 窗口和真实游戏适配。 |
| SdCardPath | 2 | 外部 RetroArch 的核心目录，不能换成本应用目录。 |
| ViewConstructor | 1 | GBA View 由指定 Activity 代码创建，不新增不安全的 Context 强转。 |
| IconLauncherShape | 5 | 旧系统品牌位图形状；不为消除诊断重绘品牌图像。 |
| SDK / 构建 / 依赖升级 | 9 | 保持已验证工具链和运行库，升级另做原生及 UI 兼容验证。 |

## 构建与包体

`clean testDebugUnitTest assembleDebug assembleRelease lintRelease` 通过。67 项 JVM 测试中 65 项通过，2 项需要在线凭据的测试未启用。新增测试覆盖取消、旧请求忽略取消后仍不得回写、页面退出和失败重试；已有网络测试验证阻塞 IO 的超时与中断。

首次启动 Gradle 遇到本机 `FileLockContentionHandler / java.net.BindException: Address already in use: bind`，另一次 PowerShell 未加引号的 `-D` 参数被拆成任务名；调整启动参数后完整构建通过，均非 Kotlin 编译错误。

| APK | 1.3.4 字节 | 1.3.5 字节 | 减少 |
|---|---:|---:|---:|
| ARM64 | 33,313,346 | 31,694,629 | 1,618,717（1.54 MiB） |
| ARM32 | 29,397,227 | 28,977,916 | 419,311（0.40 MiB） |

ARM64 保留 10 个、ARM32 保留 9 个 native 库，SHA-256 均与上版相同；没有新增 native 库。两包 apksigner 验证通过，证书 SHA-256 为 `5cfa24f9c6b674b42771579d862983b4914b674433805d322e4b8d527e9f8610`，保持覆盖更新签名。

三语 630 个键同步；硬编码文本扫描未引入新的可见硬编码文本。模拟器活动实现、按键分发、A/B/L3/R3、5 槽与快存、金手指重启恢复和外部启动 Intent 未修改。

## 真机验证

- vivo V2310 / Android 15 / 1612×720 / density 300：**36 项通过**（三主题各 11 项 UI、3 项独立存储回归）。覆盖两入口默认封面、加载中类型/查询操作、24 图完整滚动、图片页返回、更新说明/备份弹窗、元数据、开关/滑块、备份恢复和手柄组合设置。波浪测试通过推进 Compose 时钟比较像素，确认 Glass/MIUIX 开启时变化、关闭时稳定，M3 始终稳定。
- 使用用户临时凭据访问官方 TheGamesDB：**3 项通过**。Incredibox 默认封面→立即切截图→切回封面→下载保存，宫格字段不被覆盖；英文搜索及认证错误提示通过；Advance Wars / 高级战争补全发行日期、类型、开发商/发行商和两种可解码图片。宽泛系列名“星之卡比”没有误匹配。未保存凭据或改真实游戏库。
- 同一设备 density 160 模拟平板逻辑宽度：**12 项专项回归通过**，覆盖三主题默认图片类型、加载中操作、24 图滚动及背景开关，检查 Glass/MIUIX 手机与平板截图。测试后恢复 density 300；这不是另一型号平板硬件实测。

没有 ROM 或实体手柄实玩，不能把构建和 UI 回归视为所有游戏兼容性验证。ARM32 原本缺少 Nestopia 的限制记录在优化评估中，没有冒险换成未经验证的核心。性能评估没有声称测得 FPS 或耗电改善；Macrobenchmark、R8、可选核心包及新增模拟器均是后续建议，未混入本次实现。
