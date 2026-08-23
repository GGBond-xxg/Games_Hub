# Changelog

Only current and high-value milestones are retained here. Detailed experimental notes were removed from the handoff package to keep project context focused.

## 1.0.2

- Refresh the installed Android app list every time Android app management is opened from Settings.
- Newly installed launchable apps now appear without restarting GameHub.
- Preserve existing Android game tags, favorites, ordering and launch behavior during refresh.

## 1.0.1

- Fixed Monet dynamic colors being bypassed while the launcher was in dark mode.
- Changed the default manual theme color to pink.
- Added persistent pink, blue, purple, green and orange theme presets when Monet is disabled.
- Kept dynamic wallpaper colors active on Android 12 and later while preserving the existing launcher layout and controls.

## 1.0.0

- Completed the planned built-in emulator lineup: GBA, GB/GBC, FC/NES, SFC/SNES, MD/Genesis, PS1, N64 and Arcade.
- Added per-platform selection between built-in and supported external emulators.
- Added N64 header detection and automatic byte-order normalization for `.z64`, `.v64`, `.n64` and incorrectly named compatible files.
- Added the MAME 2003-Plus Arcade runtime with six-button touch controls and shared save/menu behavior.
- Added ARM64 and ARM32 release APK splits to reduce download size without removing emulator features.
- Preserved launcher list/grid layouts, right-side preview artwork, favorites, custom ordering and controller shortcuts.
- Added release signing support, synchronized English/Simplified Chinese/Traditional Chinese text and refreshed public documentation.

## 0.1.93

- Reworked the display editor into separate preview-artwork and grid-artwork cards.
- Moved display-name editing into the top current-name control.
- Kept wide layouts side by side and narrow layouts scrollable.
- Removed irrelevant footer actions from detail/back screens.
- Synchronized new text across all three i18n files.

## 0.1.92

- Split custom artwork into `previewImagePath` and `gridImagePath` with legacy migration.
- Moved list/grid controls to icon buttons beside the launch hint.
- Added tablet-first grid and phone-first list defaults while preserving user choice.

## 0.1.91

- Added list/grid layouts across launcher collections.
- Added responsive 1–4 column grid while retaining the right preview.
- Tightened non-Chinese footer shortcut spacing.

## 0.1.90

- Separated physical-controller opacity, touch-control opacity and virtual-button editing.
- Shared internal control preferences across supported built-in emulators.

## 0.1.88

- Added SFC/SNES platform and internal Snes9x core path.
- Reused the common internal emulator menu, save and touch-control conventions.

## 0.1.87

- Standardized the user-visible brand as GameHub.
- Kept package identity `com.bond.md3elauncher` for upgrade/data compatibility.

## Key earlier milestones

- **0.1.85:** synchronized language state across separate emulator processes and closed emulator processes on normal exit.
- **0.1.78–0.1.82:** established JSON-based English, Simplified Chinese and Traditional Chinese UI text.
- **0.1.76:** added GB/GBC as an independent launcher platform using the mGBA runtime.
- **0.1.74:** introduced common internal emulator menu and GBA-style virtual-control rules.
- **0.1.73:** fixed FC/NES internal emulation on Nestopia only.
- **0.1.64:** added the first internal FC/NES path.
- **0.1.56:** stabilized multiple GBA cheats by assigning one custom cheat per libretro slot.
- **0.1.55:** stabilized cheat disabling with quick-save, restart and automatic quick-load.
