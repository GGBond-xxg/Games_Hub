# v1.3.0 UI 检查与验证

## 实现依据与修正

- M3 保留 Material3 原组件、动态取色和手柄回调。Glass、MIUIX 通过共享适配层接入，根组合结构稳定，切换风格不会清除当前导航和页面状态。
- MIUIX 依据 v0.9.4 的 `basic/Card.kt`、`Button.kt`、`TextField.kt`、`Slider.kt`、`Switch.kt` 和 `theme/Colors.kt` 接入实际组件；共享页面的语义颜色映射到 MIUIX token。输入提示、禁用状态、已有点击回调保留。
- Glass 依据 AndroidLiquidGlass / Backdrop 1.0.6 的 `drawBackdrop`、`effects/blur`、`effects/lens` 和 catalog `components/LiquidSlider.kt`。滑块采用细轨道与玻璃胶囊外观，沿用 Compose Slider 的触摸、键盘和无障碍调节逻辑；拖动放大，不复制 catalog 的手势状态机。
- Liquid Glass Foundations 为 SwiftUI 指南，本项目采用其内容清晰与浮层层次原则，不调用 iOS API。按用户要求将可用的启动器控件、设置表面和列表操作层扩展为 Glass；封面、截图和模拟器游戏画面不做模糊。
- 按钮组横向、换行纵向均使用 8dp 间隔；主题选择与设置入口留出间隔；排序上下移按钮分开。Glass 设置弹窗限制最大高度并裁剪背景边界。
- 正式包检查发现矩形标签不能使用 Backdrop 的 lens 效果，已按上游形状契约限制折射；矩形保留模糊，并加入实际渲染回归。
- 平台管理及模拟器列表移除前置序号，长按排序保留。右侧预览与四列上限保留，A/B、L1/R1、L3/R3 共享回调未删除。

## 已完成验证

- vivo V2310 / Android 15，同签名覆盖升级。
- 1612×720 横屏，原始 300 dpi 手机逻辑宽度及临时 160 dpi 平板逻辑宽度，检查设置、平台/模拟器列表、主题条件显示、Glass 设置与按钮间距；测试后恢复原始密度。
- `ThemeAndMetadataTest`：每种主题测试资料四字段保存、批量确认与取消、主题切换记忆状态和滑块调节语义。手机横屏 9/9、平板横屏 9/9，通过隔离 composition 运行，不启动主页面、不写游戏库、不使用服务凭据。
- 干净 Debug/Release 构建、Release lint、52 项 JVM 测试通过。两个 ARM APK 均保持原发布签名。
- JVM 测试覆盖资料解析、错误平台/模糊/歧义匹配拒绝、封面截图分离、只补缺失、非空资料保留、Glass 参数边界及既有启动/备份等逻辑。
- 三种语言 622 个键同步。硬编码文字脚本仍报告既有历史文本；新增 UI 文案均从 I18n 读取。

## 验证边界

- 未使用用户的 ScreenScraper 凭据进行在线资料抓取；服务实际额度和地区网络仍需设备实测。Libretro 只提供图片，资料需要 ScreenScraper 开发者凭据或手动输入。
- 设备系统文件选择器拒绝选择测试目录，因此未完成真实 ROM 库的在线批量端到端验证；不将隔离 UI 测试等同于真实服务成功率。
- 未验证实体手柄、Android 7–12 的 GPU 降级效果、RTL 和所有字体缩放组合。滑块保留标准语义，旧系统使用非折射表面。

参考：[MIUIX v0.9.4](https://github.com/compose-miuix-ui/miuix/tree/v0.9.4)、[AndroidLiquidGlass 1.0.6](https://github.com/Kyant0/AndroidLiquidGlass/tree/1.0.6)。
