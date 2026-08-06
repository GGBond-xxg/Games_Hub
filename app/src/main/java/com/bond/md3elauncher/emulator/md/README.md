# MD / Genesis Runtime

Current configuration:

- Platform: `PlatformKind.MD`
- Internal marker: `internal:md`
- Core: `libgenesis_plus_gx_libretro_android.so`
- Runtime host: `emulator/md/InternalMdActivity.kt`
- Process: `:internal_md`
- Scan types: `.md`, `.gen`, `.smd`, `.bin`, `.zip`, `.7z`

The first phase supports cartridge games only. Sega/Mega CD and 32X are not
enabled. `.7z` is scan-only until a dedicated extractor is added.

MD reuses the common five-slot plus quick-save menu, reset/restart/exit actions,
controller shortcuts, and GBA-style touch controls. The touch mapping exposes
the six-button pad through A/B/C/X plus Y/Z shoulder pills and labels Select as
Mode.
