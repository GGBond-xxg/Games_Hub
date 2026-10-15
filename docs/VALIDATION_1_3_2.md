# v1.3.2 修复与验证

## 当前版本闪退

在 vivo V2310 / Android 15 的 v1.3.1 正式包上，设置 → 当前版本可复现 `IllegalStateException: Vertically scrollable component was measured with an infinity maximum height constraints`。Glass/MIUIX 弹窗已有滚动容器，更新内容又添加了一层无高度约束的滚动容器。

统一由 `HubAlertDialog` 管理三主题的正文滚动，移除更新、备份、恢复确认、Activity 选择和其他平台列表内重复的滚动容器。标题和操作按钮留在滚动区域外。保留检查更新、重试、下载、安装和发布页面等原有动作。

## 11 项 lint 错误的处理

- 1 项 `NewApi`：`windowLayoutInDisplayCutoutMode` 移入 `values-v28`；API 24–27 使用基础主题，API 31+ 继续使用原启动屏设置。公共属性通过基础主题继承，避免主题分叉。
- 9 项 `RestrictedApi`：三个 Activity 的公开 `dispatchKeyEvent` 回调均继承自 `androidx.activity.ComponentActivity`。lint 追溯到了带类级限制的 `androidx.core.app.ComponentActivity`。在这三个回调上添加有原因的局部注解，保留原有 `super.dispatchKeyEvent` 和所有按键处理分支；旧根目录副本只加注解，没有实现新功能。
- 1 项 `QueryAllPackagesPermission`：保留启动器所需的广泛应用可见性，并在这一条权限声明上记录局部豁免。自定义模拟器配置和厂商应用发现回退依赖此权限；没有关闭整个 lint 规则，也没有添加全局 baseline。

参考：[AndroidX ComponentActivity 源码](https://github.com/androidx/androidx/blob/androidx-main/core/core/src/main/java/androidx/core/app/ComponentActivity.kt)、[Android 应用可见性说明](https://developer.android.com/training/package-visibility/declaring)。Google Play 上架仍需独立审核该权限；本次继续使用 GitHub 分发。

## 行为保护

- 三个模拟器 Activity 去除新增注解/注释后与 v1.3.1 逐字一致；未更改快捷键顺序、按下/抬起状态、菜单处理、核心输入或系统回退。
- 启动器 A/B/L3/R3 处理、外部模拟器启动、存档和金手指实现未改动。
- ARM64 包的 12 个、ARM32 包的 10 个原生共享库与 v1.3.1 字节完全相同；两种 APK 均通过发布签名验证。
- 没有 ROM 可用于真实游戏运行验证；不把构建成功或静态对比表述为已完成全部模拟器实玩测试。

## 检查结果

- `clean testDebugUnitTest assembleDebug assembleRelease lintRelease` 成功；最终版本再执行构建、JVM 测试和 lint。
- JVM：60 项通过，1 项依赖临时密钥的在线测试默认跳过，无失败。
- 完整 `lintRelease`：0 errors / 111 warnings / 1 hint；既有警告未在本次扩展清理。
- 三语 626 个翻译键同步，无新增用户可见文案；硬编码脚本仅保留既有报告。

- 手机横屏 1612×720 / 300dpi：21 项首次通过；3 项长说明/返回测试补上弹窗显示等待并改用原生按键注入后 3/3 通过。强化了真实滚动到末尾的断言；未通过的早期运行不作为通过证据。
- 平板宽度（同一设备临时 160dpi）：最终测试包 24/24 通过，涵盖三主题的版本检查失败重试、80 行更新说明滚动到底、取消和 Back 返回、备份入口、元数据、批量取消、主题状态、滑块开关和图片搜索。检查手机/平板弹窗截图，操作按钮保持可见。
- 最终 v1.3.2 Release 在实机覆盖安装成功（相同发布签名，非 debuggable）；实际设置中的“当前版本”联网检查正常、取消可用，GameHub 进程持续存活且没有新增 crash 日志。测试包已移除，密度恢复 300dpi。
- 实机注入 gamepad B 可从设置返回；该 vivo 上注入 Y 会返回系统桌面，但 GameHub 进程仍存活，无崩溃日志。本轮不把该现象归因为本次修复，也不宣称完成全部实体手柄回归；相关源码完全未变。
