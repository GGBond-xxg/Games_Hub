# SFC / SNES Runtime

Current configuration:

- Platform: `PlatformKind.SFC`
- Internal marker: `internal:sfc`
- Core: `libsnes9x_libretro_android.so`
- Runtime host: `emulator/fc/InternalFcActivity.kt` with SFC mode extras
- Scan types: `.sfc`, `.smc`, `.swc`, `.fig`, `.bs`, `.st`, `.zip`, `.7z`

SFC/SNES must reuse the common internal emulator menu, save-state model and GBA-style virtual controls. `.7z` is scan-only until a dedicated extractor is added.
