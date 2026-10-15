# v1.3.3 lint 与图片搜索验证

## 英文搜索排查

用户反馈 Incredibox 在预览图和宫格图入口均找不到，但官网有封面。

- 先前临时密钥被官方接口明确返回 HTTP 403 / `Invalid API key was provided`；官网搜索不依赖该密钥，因此官网有结果不能证明 API 认证有效。
- 使用新的临时密钥，`Incredibox` 官方 API 返回游戏 104438 / PC（平台 1）。图片目录有 `boxart/front/104438-1.jpg` 和 banner，没有 screenshot。Android 应用手动搜索没有 Android 平台过滤，能取得 PC 条目的素材；没有修改自动刮削的平台匹配规则。
- 手动搜索新增“封面 / 截图”选项。预览图默认截图，宫格图默认封面，两者都允许用户主动切换；保存仍分别写入原有字段，批量刮削不以封面充当截图。
- 当没有结果且 API 出现认证、额度或网络错误时，主提示显示具体错误，避免仅显示“没有找到封面”。

公开条目：[TheGamesDB / Incredibox](https://thegamesdb.net/game.php?id=104438)。临时密钥只通过测试进程环境或 instrumentation 参数传入，不进入源码、文档或发布包。

## lint 安全清理

完整 `lintRelease` 从 0 errors / 111 warnings / 1 hint 降至 **0 errors / 61 warnings**。修复 50 个警告和 1 个提示，没有新增全局忽略或 baseline。

- 35 处 KTX 建议：URI、Bitmap、简单 SharedPreferences apply 事务采用等价内联扩展；配置键、值、异步 apply 和双图片字段保持不变。外部启动器只替换 `Uri.parse` 的等价包装，Intent 参数及尝试顺序不变。
- 两个 Composable modifier 参数调整为首个可选参数，调用点均使用命名参数；整数选择状态使用 `mutableIntStateOf`；Glass 开关动画 offset 延后到布局阶段计算。
- 删除最低 API 24 下永远不会运行的旧语言分支；跨进程语言文件读写不变。
- 删除三项无引用资源：两张旧 bitmap 和一个未使用的 monochrome drawable。实际 launcher 的 mipmap monochrome 未改，未使用反射动态资源查找；一并消除 XML/PNG 同名提醒。
- 七处直接依赖声明转入版本目录，所有版本保持原值；不升级 Compose、Glass、MIUIX、LibretroDroid、Gradle 或 AGP。

## 保留的 61 个警告

| 类型 | 数量 | 保留原因 |
|---|---:|---|
| UseKtx | 20 | 位于模拟器、旧重复文件、跨进程语言、游戏库和备份事务；只为语法建议改写核心路径收益低。备份恢复检查 `commit()` 返回值，不能改成返回 Unit 的 KTX edit。 |
| IconLauncherShape / IconDuplicates | 15 | 旧系统 launcher 图标形状与 round 图标共用图片；修复会改变图标设计或资源选择，留待单独处理。 |
| DiscouragedApi | 7 | 模拟器固定横屏行为；需 Android 16+ 设备和真实游戏单独做方向/窗口适配。 |
| SDK / 依赖版本提醒 | 9 | 目标 SDK、Gradle、AGP、Compose、Lifecycle、Backdrop、Kotlin、coroutines、JSON 升级应独立评估兼容性。 |
| SetTextI18n | 4 | `circle` 是样式输入值，`CodeBreaker/GameShark` 为类型标识；包含旧重复文件，未把解析值改为本地化文案。 |
| ClickableViewAccessibility | 3 | 模拟器多点触控、拖动和菜单输入需设计独立无障碍动作；没有机械地给每次抬手补 click。 |
| SdCardPath | 2 | 传给 RetroArch 的第三方核心路径，不能替换为 GameHub 自己的 filesDir。 |
| ViewConstructor | 1 | 自定义触控 View 通过指定 GBA Activity 创建，不由 XML inflate；没有新增不安全的 Context 强转构造。 |

## 验证范围

- `clean testDebugUnitTest assembleDebug assembleRelease lintRelease` 构建通过；版本设为 133 后再次构建、测试、lint 通过。JVM 共 63 项通过，包含启用临时凭据的两项官方 API 实测，无跳过。
- vivo V2310 / Android 15 / 1612×720 / density 300：三主题界面、设置存储和联网刮削共 27 项通过；另增加的 Incredibox 预览图选封面、下载、保存流程 1 项通过，断言宫格字段保持为空。
- 同一设备 density 160 模拟平板逻辑宽度：三主题 24 项界面回归通过，包括图片类型切换、凭据保存、开关/滑块、当前版本长说明及备份弹窗；检查手机和平板宽度搜索页截图，未出现控件重叠或操作入口丢失。这不等同于不同型号平板的硬件验证。
- 中文“高级战争”和英文 Advance Wars 的批量处理均取得可解码封面、独立截图与完整资料；宽泛“星之卡比”查询保持歧义跳过，不自动套错游戏。这里使用标题样本，没有读取真实 ROM 库。
- 三语 630 个键完全同步，硬编码扫描为存量告警；`git diff --check` 通过。
- 两种正式 APK 签名校验通过，证书与 v1.3.2 一致；ARM64 的 12 个、ARM32 的 10 个原生库逐一对比均相同。

- 模拟器目录、旧 GBA Activity、启动器手柄分发和原生核心没有修改。
- 没有 ROM 或实体手柄用于本轮游戏实玩验证；不将静态对比或 ADB 注入等同于全部模拟器实玩。
- 新增独立 SharedPreferences 回归：只使用随机 QA 名称，不读取或覆盖用户的 launcher store、语言文件或真实游戏库。
