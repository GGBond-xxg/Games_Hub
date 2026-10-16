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

## Sega 8-bit systems

SMS, Game Gear and SG-1000 reuse this Activity and the same packaged core in
`:internal_md`. `Sega8BitSystem.EXTRA_SYSTEM` is required to distinguish them from
MD; the cold-reset relay preserves this extra. Do not infer the mode from the
shared core filename. Existing MD options, six-button controls and save paths
remain unchanged.

| Platform | Marker | ROM / ZIP entry | Core system | Save directory |
|---|---|---|---|---|
| SMS | `internal:sms` | `.sms` | `master system` | `internal_sms/game_data` |
| Game Gear | `internal:gg` | `.gg` | `game gear` | `internal_gg/game_data` |
| SG-1000 | `internal:sg1000` | `.sg` | `sg-1000` | `internal_sg1000/game_data` |

BIOS loading and Game Gear extended-screen mode are disabled for these modes.
ZIP extraction selects only the corresponding platform extension; extract 7z
before launching internally. No ROM or BIOS is bundled.

The common host provides five state slots, quick save/load, soft restart, cold
reset and process-closing exit. Backups include all three independent state roots.
As in the existing shared host, automatic SRAM serialization on lifecycle events
is disabled; use the normal/quick state slots for persistent progress.

The two-button pad uses B = 1 and A = 2; touch turbo labels are 1/2. SMS and SG-1000
use Start as the console Pause signal, Game Gear retains Start. Hardware shortcuts
are loaded from the existing shared settings, including quick save/load, turbo,
fast-forward, menu and exit. The touch shoulder game buttons are hidden only for
these three systems; their physical shortcut bindings remain available.
