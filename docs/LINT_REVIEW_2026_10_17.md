# Lint 安全清理记录（2026-10-17）

本次在 v1.3.6 上做局部维护，版本号保持 `1.3.6 / 136`，不替换已有 GitHub Release。

## 已处理

原报告 26 项中处理 8 项，本次完整扫描为 **0 错误、19 警告**。差额来自新发现的一项 Gradle wrapper 更新提示（9.4.1 → 9.8.1），并非新增运行时代码问题。

- **2 项 SdCardPath**：RetroArch 的核心目录从固定 `/data/data/<package>/cores` 改为读取目标应用的 `ApplicationInfo.dataDir`。不是 GameHub 自己的 dataDir；不改变核心候选顺序、ROM 缓存、Intent action/flags、配置文件参数或内置核心。查不到目标应用时使用已有通用启动回退。注意：原有 RetroArch `CONFIGFILE` 的用户 0 路径本次未改，不宣称已完整支持 Android 多用户启动。
- **1 项 ViewConstructor**：GBA 控件使用标准 `Context` 构造签名，内部仍由 `InternalGbaActivity` 宿主持有，与现有 FC 控件的方式一致。唯一创建入口仍传入同一个 Activity；触摸处理、菜单、按键和存档实现不变。
- **5 项 IconLauncherShape**：保留五种密度的原始 PNG 字节，在 Android 7（API 24/25）启动图标外增加 4dp 透明留白。API 26+ 自适应图标不变。Lint 追踪到内部 PNG 时忽略了 XML inset，因此 `app/lint.xml` 仅对这五个内部素材精确排除误报，并注明原因；不是全局关闭图标检查。设备渲染测试验证各边透明、原始红白图案仍显示及现代图标仍解析为 AdaptiveIconDrawable。

清理三份确认未使用的旧文件，共 338 行：`emulator/gba/GbaNativeCheatBridge.kt`、`cpp/gba_cheat_bridge.cpp`、`cpp/CMakeLists.txt`。检查了 Kotlin/Java 引用、Manifest、JNI 导出、库加载和 Gradle/CMake 接入：占位 Kotlin 对象没有调用方；C++ 导出指向已删除的旧根包桥接；没有启用 externalNativeBuild，也没有对应桥接库进入 APK。活动 GBA 金手指仍通过 LibretroDroid，禁用时的快存 → 重启 → 快读流程未改。

## 保留的 19 项

| 类别 | 数量 | 保留原因 |
| --- | ---: | --- |
| OldTargetApi | 1 | targetSdk 升级会改变系统行为，需要独立兼容性验证。 |
| AndroidGradlePluginVersion / GradleDependency / NewerVersionAvailable | 8 | 涉及 Gradle、AGP、Compose、Lifecycle、Kotlin、协程、Backdrop 及测试 JSON 版本；不在 lint 清理中批量更新依赖。 |
| DiscouragedApi | 7 | 启动器和模拟器当前要求横屏；取消方向约束会影响现有布局及触控。 |
| ClickableViewAccessibility | 2 | Canvas 控件承担多点按下、滑动和释放，菜单部分在按下时处理；补一个无语义的 performClick 不等于提供无障碍操作，可能改变输入事件。应单独实现虚拟无障碍节点。 |
| UseKtx | 1 | 备份恢复检查同步 `commit()` 的 Boolean，失败时回滚。KTX `edit(commit = true)` 返回 Unit，直接替换会丢失写入失败检测。 |

这些保留项未加入 baseline 或禁用规则。

## 验证

- Gradle wrapper：执行 clean、assembleDebug、assembleDebugAndroidTest、testDebugUnitTest、lintDebug；首次 lint 报 `:app:lintDebug / ReferenceType`（mipmap 别名指向 drawable），已修正为同类型别名，最终构建与 lint 通过。`assembleRelease` 另行通过。
- JVM：71 项，69 通过、2 个需要联网凭据的 opt-in 用例跳过，0 失败。
- Android 15 / vivo V2310 / ARM64：8 项回归通过，包括新加的 RetroArch 启动契约 2 项、图标 2 项，以及既有设置/游戏库/手柄绑定持久化 4 项。
- 启动契约测试遍历 12 类支持平台，检查首选核心、MAIN 启动、MAIN 失败后的 VIEW 回退、缓存 ROM 内容、组件及 flags。使用已安装测试目标的真实 PackageManager 元数据并拦截 startActivity，不实际启动外部应用；设备未安装 RetroArch，不能等同于 RetroArch 实玩验证。
- 实际 Genesis Plus GX 核心：SMS、GG、SG-1000、SMS ZIP 共 4 项通过（139.839 秒），使用项目自编诊断程序。覆盖按键 1/2、Pause/Start、连发、快进、菜单、5 槽与快存保存/读回、软重启、冷重置、再次进入后快读、正常退出及组合键退出。未使用商业 ROM；按键来自 Android 注入，不是实体手柄。
- `check_i18n_keys.py`：三语 639 键同步；hardcoded-text 检查仍是既有提示，本次未新增可见文案。
- ARM64 的 10 个、ARM32 的 9 个 native 库与 v1.3.6 对应发布 APK 逐个 SHA-256 相同。签名验证通过，证书 SHA-256 仍为 `5cfa24f9c6b674b42771579d862983b4914b674433805d322e4b8d527e9f8610`。
- 无活动页面布局改动；图标在五种密度下渲染验证。未额外重复整套手机/平板 UI 流程，未实玩全部旧平台，也没有 ARM32 真机测试。启动器 A/B/L3/R3、GBA 金手指、存档路径与格式均无改动。
- 真机测试结束后确认没有 QA 存档残留，按哈希校验并删除原创诊断文件及其复制缓存，卸载测试包，覆盖安装本次正式签名构建并成功启动；设备 density 保持原值 300。

发布构建产物（本地验证，未作为新 Release 发布）：

| ABI | 字节数 | SHA-256 |
| --- | ---: | --- |
| arm64-v8a | 31,701,697 | `44caa2f641640befed6defb193f56768fe1804cdd51780902e0e804333f5df75` |
| armeabi-v7a | 28,984,984 | `a635d2fcf3973c54c0572127926ca1a391f969916dcdf83765b7fe6be20b078c` |
