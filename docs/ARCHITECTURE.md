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
- `ui/AboutAndBackup.kt`: backup document pickers, restore confirmation, project/legal information and sponsorship addresses.
- `ui/UiWidgets.kt`: reusable Compose UI pieces.

### Data and scanning

- `data/Models.kt`: `PlatformKind`, platform configuration, games, layout mode and artwork overrides.
- `data/LauncherStore.kt`: local persistence and migration logic.
- `io/RomScanner.kt`: platform extension filtering and metadata extraction.
- `io/LauncherBackup.kt`: versioned user-data backup, staged restore and rollback on handled write failures; excludes ROMs, BIOS and scraper credentials.
- `io/BackupArchive.kt`, `io/BackupPaths.kt`: bounded ZIP extraction and the portable user-file allowlist.
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
| `PS2`, `NDS`, `N3DS`, `GC`, `WII`, `DREAMCAST` | none | external emulator only |

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
- Recent launches reuse the favorites list/grid and preview components, with independent membership and actual favorite state. Launching resets any manual recent ordering to chronological order.
- Artwork decoding runs on IO with bounded dimensions, two concurrent decoders and a shared 16 MB cache. Scans and restores invalidate the cache.

## Backups and scan safety

ROM scans query document providers directly so unreadable, missing or still-loading directories fail instead of returning an empty replacement library. All-platform scans update only after every configured directory succeeds.

Backups use a versioned `manifest.json` with typed preferences and allowlisted files. Restore validates into a cache staging directory before touching live data, rewrites local artwork paths, and retains replaced files for rollback if a handled write failure occurs. It replaces settings and merges file data, overwriting matching saves only. Document permissions must be re-granted on another device; changed document URIs are not automatically matched to old game IDs. Internal emulators must exit before either operation. The archive excludes ROMs, BIOS, scraper credentials and temporary cheat-restart snapshots.

Offline legal documents in `app/src/main/assets/legal/` mirror the root `LICENSE`, `THIRD_PARTY_NOTICES.md` and `DISCLAIMER.md`; keep these copies synchronized when changing the originals.

## Internationalization

Visible text is stored in:

```text
app/src/main/assets/i18n/en.json
app/src/main/assets/i18n/zh.json
app/src/main/assets/i18n/zh-Hant.json
```

The files must contain identical key sets. Built-in emulators use separate processes, so language mode is also persisted to a file under `filesDir`; see `docs/I18N.md`.

## Legacy duplicate cleanup (1.3.5)

The unused root-package `InternalGbaActivity.kt` and `GbaNativeCheatBridge.kt`,
`system/InternalEmulators.kt`, and `io/PspIsoReader.kt` (including its private-to-that-file
usage of `PspIsoMetadata`) were removed after checking manifest components, Kotlin
imports/references, reflection, JNI symbols and build/keep configuration. Current launch
routing and ROM scanning explicitly use the corresponding `emulator/` implementations.
No active emulator, controller handler, persisted marker or save path was changed.

`cpp/gba_cheat_bridge.cpp` still contains historical JNI exports for the old root-package
bridge. This native scaffold is not connected to Gradle's `externalNativeBuild`, is not
packaged as a cheat bridge library, and had no native declarations or library loader in
the removed Kotlin scaffold. Enabling it in the future requires deliberate JNI/package
integration and testing; it is not the current LibretroDroid cheat backend.

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

## Release updates and recovery

- `system/ReleaseInfo.kt` parses official GitHub releases and selects a verified native-ABI APK; `system/GitHubUpdater.kt` bounds downloads and checks SHA-256, package, signing certificate and version before invoking Android's installer.
- `ui/UpdateSettings.kt` owns the on-demand update dialog and unknown-source permission handoff. No background update checks run.
- `data/GameLibraryCodec.kt` isolates per-record decoding and previous-valid-library recovery. `LauncherStore.saveGames` retains the prior valid document before replacing it.
- `system/LaunchPreflight.kt` checks supported formats, core/emulator availability and readable game URIs on IO before recording a launch.
- `system/CoverSources.kt` holds Libretro mappings and ScreenScraper response parsing. ScreenScraper developer and optional user credentials are supplied locally and excluded from portable backups.

## Configurable external launches

`PlatformConfig.launchConfig` defaults to disabled for existing data. `system/CustomLaunchConfig.kt` validates structured Activity/action/MIME/data-template/primitive JSON extras. `ExternalLauncher` applies enabled custom configurations exactly and keeps the original ROM URI read grant; it never executes a shell command. `ui/CustomLaunchSettings.kt` edits these fields. Selecting a different emulator resets its old launch configuration.

The additional external platforms are appended to default platform and tab sets without changing existing IDs. Their settings rows appear after configuration; game tabs use the existing nonempty-library visibility rule. No additional internal cores are included.

`CoverArtworkType` distinguishes screenshot queries (`Named_Snaps`) from box-art queries (`Named_Boxarts`). Both manual artwork slots default to box art and allow switching during a request. `ArtworkSearchController` cancels the previous request and publishes only the latest result; each request has isolated diagnostics. Batch scraping still fills separate cover/screenshot fields without substituting one type for the other.

## Metadata, batch jobs and theme styles (1.3.0)

- `data/GameMetadata.kt`: optional metadata stored in `ItemOverride`, independent of scanned games and both artwork paths. Existing non-empty values win during batch merge. Backups already include overrides and custom images.
- `system/GameScrapeSources.kt`: platform mapping, exact unambiguous matching, metadata/media parsing and missing-field policy.
- `system/GameScraper.kt`: bounded HTTPS requests, sequential source fallback, image decoding and cancellation cleanup. `MainActivity` owns the lifecycle job and persists each completed game after checking the latest override.
- `ui/GameInformation.kt`: preview metadata, manual editor and platform batch progress/actions.
- `ui/ThemeStyle.kt`, `ui/StyleComponents.kt`: persisted M3/Glass/MIUIX selection and control adapters. Stable root composition preserves navigation when switching styles. Glass records a separate gradient backdrop, avoiding recursive recording; images and gameplay are content, not glass surfaces.
- Backdrop 1.0.6 and MIUIX 0.9.4 are pinned. MIUIX requires compile SDK 37.0 and minimum API 24; target SDK remains 36. MIUIX follows system brightness, while M3/Glass keep their saved appearance settings.

## Scraping and visual fixes (1.3.1)

- `system/TheGamesDb.kt` shares manual/batch search, verified platform IDs, separate boxart/screenshot parsing and cached genre/developer/publisher dictionaries. Exact normalized title plus platform is required for batch updates; regional editions use a deterministic preference, ambiguous aliases are skipped. Credentials are supplied by the user and never included in errors or logs.
- `ui/GlassSwitch.kt` renders one glass thumb over a recorded track and retains switch semantics. Glass buttons draw one material inside their hit target, and Glass surfaces suppress redundant Material borders/elevation.
- `background_motion` is a persisted boolean, included in portable backup validation. Glass and MIUIX expose the switch; M3 stays static. The background is drawn separately from content, reads animation state only during drawing, and animates only while the lifecycle is resumed.
