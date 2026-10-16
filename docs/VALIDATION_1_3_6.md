# v1.3.6：SMS、Game Gear、SG-1000 内置模拟器

## 实现与使用

- 设置 → 平台管理 → 模拟器 → 添加其他，选择 SMS、Game Gear 或 SG-1000，选择 ROM 目录并扫描。
- 复用已打包的 Genesis Plus GX 和 `InternalMdActivity` / `:internal_md`，以显式系统参数区分三个模式与 MD。核心选项强制对应硬件，禁用 BIOS 加载，Game Gear 保持原始画面范围。没有新增或替换 native 库。
- 对应原始格式是 `.sms`、`.gg`、`.sg`；普通 ZIP 只提取所选平台的格式，跳过其他平台文件。7z 可扫描，但内置启动前提示先解压。没有提供 ROM、BIOS 或固件。
- 复用五槽即时存档、快存/快读、重启、重置、退出菜单和现有手柄快捷键。B 对应按键 1，A 对应按键 2；SMS / SG-1000 的 Start 发出 Pause，GG 保留 Start。触摸面板隐藏不适用的肩部游戏按钮，实体肩键快捷功能不变。
- `internal_sms`、`internal_gg`、`internal_sg1000` 独立保存数据；备份允许五槽、快存和 SRAM 文件，不允许 ROM/BIOS。和原有共享宿主一致，生命周期自动写 SRAM 仍关闭，持久进度使用即时存档或快存。
- 追加平台配置、排序和入口，已有平台设置、外部模拟器绑定及旧存档路径保持不变。接入三平台的 Libretro / TheGamesDB / ScreenScraper 平台映射，已有手动与批量刮削流程可复用。

## 构建、静态检查与包体

使用 Gradle Wrapper，完成 `clean assembleDebug`、`assembleRelease`、`assembleDebugAndroidTest`、`testDebugUnitTest`、`lintDebug`；最终源码修改后再次运行构建、测试及 lint，全部成功。测试安装包使用本机发布签名的临时 Gradle init 配置，未修改项目默认 debug 签名。

- JVM：71 项，**69 通过、2 跳过、0 失败**；跳过的是需凭据的可选网络测试。新增覆盖默认/显式/外部绑定、格式隔离、共享核心、独立存档根、平台映射、备份提取与拒绝非法路径。
- lint：**0 error、26 warnings**，未扩大历史警告屏蔽范围。
- 三语 **639 个键同步**，硬编码检查没有引入新的可见硬编码文本。
- 首轮 `:app:compileDebugAndroidTestKotlin` 曾因参数化测试混合 String/Boolean 数组推断交叉类型而失败，显式改为 `arrayOf<Any>` 后通过。

| APK | 1.3.5 字节 | 1.3.6 字节 | 增加 |
|---|---:|---:|---:|
| ARM64 | 31,694,629 | 31,701,085 | 6,456（约 6.3 KiB） |
| ARM32 | 28,977,916 | 28,984,372 | 6,456（约 6.3 KiB） |

ARM64 的 10 个、ARM32 的 9 个 native 库与 v1.3.5 逐个 SHA-256 相同。两包签名验证通过，证书 SHA-256 保持 `5cfa24f9c6b674b42771579d862983b4914b674433805d322e4b8d527e9f8610`，支持覆盖升级。

## 真机测试

设备：vivo V2310，Android 15，ARM64，1612×720，原始 density 300。

1. **4 项真实核心测试通过**：SMS、GG、SG-1000 原始文件及 SMS ZIP。运行自行编写的 Z80 诊断程序，以红/绿/蓝/黄画面断言实际核心状态；覆盖按键 1/2、Pause/Start、连发、快进、菜单快捷键、五个槽和快存的非空保存与读回、正常槽/快存颜色恢复、软重启保持进程、冷重置更换进程并保留平台与存档、正常退出关闭进程、再次进入读取快存、组合键退出。ZIP 测试先放一个错误平台文件，确认它不会被当作目标 ROM。
2. **手机横屏 22 项通过**：三个平台 × 三主题 × 两种 UI 流程，加 4 项存储回归。覆盖添加弹窗滚动到末尾平台、目录选择、扫描、外部绑定、切回内置、返回，及旧平台相对顺序/目录/模拟器绑定、图片字段、收藏、主题、语言和手柄绑定持久化。
3. **平板逻辑宽度 18 项通过**：同一设备 density 160，三平台三主题重复两种 UI 流程。已检查代表截图，无按钮遮挡或动作不可达；测试后恢复 density 300。这不是另一台平板硬件测试。

早期诊断中遇到首帧等待及用例之间残留进程；后续明确等待画面并在失败时清理测试进程。测试首次退出后再次启动曾只返回中继的主界面，已修正测试自身的独立任务启动方式。SG-1000 诊断软重启曾未清理 VDP 地址锁存，已在自写诊断程序启动时读取状态寄存器；最终四项完整重跑通过，没有为迁就测试改变生产模拟器的重启行为。

### 复现核心测试

`python scripts/create_sega8_diagnostics.py` 在被忽略的 `build/verification/v136-probes` 生成三个原创诊断程序及 ZIP。构建并安装同签名的 debug 与 androidTest APK 后，将生成文件推送至应用外部缓存 `.../Android/data/com.bond.md3elauncher/cache/qa-sega8`，运行：

```text
adb shell am instrument -w \
  -e class com.bond.md3elauncher.emulator.md.Sega8RuntimeTest \
  -e sega8_probe_dir /sdcard/Android/data/com.bond.md3elauncher/cache/qa-sega8 \
  com.bond.md3elauncher.test/androidx.test.runner.AndroidJUnitRunner
```

不提供参数时该测试跳过。运行期间保持设备解锁，勿同时操作其他应用或启动另一轮 instrumentation。生成的 ROM 文件不进入 Git 或 APK；测试保存目录使用唯一 QA 名称，正常结束删除。生成器整理前后的诊断字节哈希相同。

## 范围与限制

此次使用 Android 注入的手柄键事件，未连接实体手柄实玩，也没有使用商业游戏 ROM 验证全库兼容性；ARM32 完成构建、库哈希和签名检查，尚无 ARM32 真机。现有 GBA 金手指流程、MD 六键映射、旧模拟器核心和启动器 A/B/L3/R3 处理未改，不能将这些检查等同于所有旧游戏实玩回归。

核心支持范围参考 [Libretro Genesis Plus GX 文档](https://docs.libretro.com/library/genesis_plus_gx/)，显式硬件选项与双键映射核对 [上游 libretro 实现](https://github.com/libretro/Genesis-Plus-GX/blob/master/libretro/libretro.c)。
