# Third Party Notices

## Nestopia libretro core

v0.1.73 起，内置 FC/NES 模拟器固定使用用户从 Libretro Android arm64-v8a nightly 下载的 Nestopia 预编译 core：

```text
app/src/main/jniLibs/arm64-v8a/libnestopia_libretro_android.so
```

Mesen / FCEUmm 已从内置 FC/NES core 中移除，避免 Mesen native 闪退和 FCEUmm 对部分 Mapper 115 汉化/改版 ROM 黑屏。

正式分发 APK 前，需要继续确认 Nestopia、LibretroDroid 以及其他内置 libretro core 的许可证、源代码提供和署名要求。

## Lemuroid / 旧 FCEUmm 记录

v0.1.64 - v0.1.72 曾使用用户提供的 Lemuroid cores 包中的 FCEUmm libretro 预编译核心。

相关 GPL 文本仍保留在：

```text
third_party/lemuroid/COPYING
```

该记录仅用于历史版本说明；当前 v0.1.73 源码包中不再内置 FC/NES 的 FCEUmm core。

## Snes9x libretro core

This project bundles the Snes9x libretro Android core for the built-in SFC/SNES emulator. The binary is sourced from the existing Lemuroid/libretro core package already used by this project workflow. Keep the upstream license notices when redistributing.
