# GameHub Architecture

This document describes the current active structure. It intentionally excludes detailed historical version notes.

## Runtime processes

| Process | Main component | Purpose |
|---|---|---|
| Main app process | `MainActivity` | Launcher, settings, scanning, cover editing and external app launch |
| `:internal_gba` | `emulator.gba.InternalGbaActivity` | GBA and GB/GBC internal emulation |
| `:internal_fc` | `emulator.fc.InternalFcActivity` | FC/NES and SFC/SNES internal emulation |
| `:internal_md` | `emulator.md.InternalMdActivity` | MD/Genesis internal emulation |
| `:internal_ps1` | `emulator.ps1.InternalPs1Activity` | PS1 internal emulation |
| `:internal_n64` | `emulator.n64.InternalN64Activity` | N64 internal emulation |
| `:internal_arcade` | `emulator.arcade.InternalArcadeActivity` | Arcade internal emulation |

Internal emulator processes may retain process-local state. Normal exit finishes the Activity and then terminates the emulator process so language and emulator state are fresh on next launch.

## Main data flow

```text
User selects platform folder
        ↓
io/RomScanner.kt scans supported files
        ↓
data/LauncherStore.kt persists platform/game/customization state
        ↓
Compose launcher renders list or grid
        ↓
A launch request chooses one path:
  ├─ emulator/InternalEmulators.kt → internal Activity
  └─ system/ExternalLauncher.kt    → Android Intent to external App
```

## Active source map

### Launcher and UI

- `MainActivity.kt`: Activity setup, internal emulator launch and top-level callbacks.
- `ui/LauncherApp.kt`: main Compose state and screen coordination.
- `ui/BeaconScreens.kt`: launcher content screens.
- `ui/BeaconChrome.kt`: shared top/bottom chrome and shortcut hints.
- `ui/EditItemDialog.kt`: display-name, preview-image and grid-image editor.
- `ui/SettingsScreens.kt`: system, platform, appearance, scraper and controller settings.
- `ui/UiWidgets.kt`: reusable Compose UI pieces.

### Data and scanning

- `data/Models.kt`: `PlatformKind`, platform configuration, games, layout mode and artwork overrides.
- `data/LauncherStore.kt`: local persistence and migration logic.
- `io/RomScanner.kt`: platform extension filtering and metadata extraction.
- `emulator/psp/PspIsoReader.kt`: PSP ISO `PARAM.SFO` and local artwork extraction.

### Emulator selection

- `emulator/InternalEmulators.kt`: package markers and internal-emulator routing.
- `system/AndroidAppRepository.kt`: installed App discovery and emulator recognition.
- `system/ExternalLauncher.kt`: generic and emulator-specific Intent attempts.

### Internal emulators

- `emulator/common/`: common menu specification, host contract and GBA-style touch layout.
- `emulator/gba/`: GBA/GB/GBC Activity, controls, models and cheat flow.
- `emulator/fc/`: FC/NES Activity and controls; the same Activity currently accepts SFC mode.
- `emulator/sfc/`: SFC-specific documentation/resources; no separate Activity yet.
- `emulator/md/`: MD/Genesis Activity wrapper and platform-specific documentation.
- `emulator/ps1/`: PS1 Activity wrapper and PCSX-ReARMed/HLE documentation.
- `emulator/n64/`: N64 Activity wrapper and Mupen64Plus-Next/GLES documentation.
- `emulator/arcade/`: Arcade Activity wrapper and MAME 2003-Plus romset documentation.

## Platform mapping

| `PlatformKind` | Internal marker | Runtime/core |
|---|---|---|
| `GBA` | `internal:gba` | `InternalGbaActivity` + mGBA |
| `GB` | `internal:gbc` | `InternalGbaActivity` + mGBA |
| `NES` | `internal:fc` | `InternalFcActivity` + Nestopia |
| `SFC` | `internal:sfc` | `InternalFcActivity` SFC mode + Snes9x |
| `MD` | `internal:md` | `InternalMdActivity` + Genesis Plus GX |
| `PS1` | `internal:ps1` | `InternalPs1Activity` + PCSX-ReARMed |
| `N64` | `internal:n64` | `InternalN64Activity` + Mupen64Plus-Next |
| `ARCADE` | `internal:arcade` | `InternalArcadeActivity` + MAME 2003-Plus |
| `PSP` | none | external emulator only |
| `SWITCH` | none | external emulator only |

## Artwork model

`ItemOverride` stores two independent paths:

```kotlin
previewImagePath // right-side large preview
gridImagePath    // left-side grid card
```

Older data used one artwork path. Migration must copy that value into both current fields so existing custom artwork is not lost.

## Launcher layout state

- `LauncherLayoutMode.LIST` and `GRID` are persisted.
- Tablet-class first launch defaults to grid when `smallestScreenWidthDp >= 600`.
- Phone-class first launch defaults to list.
- Manual user selection overrides the device default.
- Grid has 1–4 columns and does not replace the right preview pane.

## Internationalization

Visible text is stored in:

```text
app/src/main/assets/i18n/en.json
app/src/main/assets/i18n/zh.json
app/src/main/assets/i18n/zh-Hant.json
```

The files must contain identical key sets. Built-in emulators use separate processes, so language mode is also persisted to a file under `filesDir`; see `docs/I18N.md`.

## Legacy duplicate source files

The repository currently contains old root-package copies that are not used by the manifest/current imports:

```text
app/src/main/java/com/bond/md3elauncher/InternalGbaActivity.kt
app/src/main/java/com/bond/md3elauncher/GbaNativeCheatBridge.kt
app/src/main/java/com/bond/md3elauncher/system/InternalEmulators.kt
app/src/main/java/com/bond/md3elauncher/io/PspIsoReader.kt
```

Current work should use the corresponding files under `emulator/` paths. Do not update both copies. These legacy files can be removed later in a dedicated cleanup after confirming no hidden compatibility requirement.

## Native resources

Libretro core libraries are stored under ABI-specific directories in `app/src/main/jniLibs/`. Packaging keeps debug symbols and uses legacy JNI packaging because stripping or repackaging may break cores.

## Persistence cautions

Avoid changing persisted keys, platform IDs, internal emulator markers or `applicationId` casually. Migrations should preserve:

- saved platform folders;
- chosen external emulator packages;
- favorites and custom ordering;
- preview/grid artwork overrides;
- controller shortcuts;
- language selection;
- save-state paths.
