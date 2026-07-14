# SFC/SNES Internal Emulator

Current implementation:

- Platform: `PlatformKind.SFC`
- Built-in package marker: `internal:sfc`
- Built-in core: `libsnes9x_libretro_android.so`
- UI host: shared internal FC activity path with SFC/SNES mode enabled by intent extras
- ROM extensions: `.sfc`, `.smc`, `.swc`, `.fig`, `.bs`, `.st`, `.zip`, `.7z`

Rules:

- Use the common GBA-style virtual button layout.
- Use the common internal emulator menu structure.
- All visible text must come from i18n JSON.
- 7z is scanned but not loaded internally yet; extract it first or use an external emulator.
