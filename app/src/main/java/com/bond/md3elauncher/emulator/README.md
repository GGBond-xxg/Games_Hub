# Emulator Module

This directory contains active internal-emulator routing and platform-specific runtime code.

```text
emulator/
├── InternalEmulators.kt          # Current internal package markers/routing
├── ControllerShortcutSettings.kt # Shared physical-controller shortcuts
├── common/                       # Shared menu, host contract and touch layout
├── gba/                          # GBA + GB/GBC runtime
├── fc/                           # FC/NES runtime and current SFC host mode
├── md/                           # MD/Genesis runtime wrapper and notes
├── ps1/                          # PS1 runtime wrapper and notes
├── n64/                          # N64 runtime wrapper and notes
├── arcade/                       # Arcade runtime wrapper and notes
├── psp/                          # PSP ISO metadata reader
├── sfc/                          # SFC-specific notes/resources
└── nse/                          # Switch launcher-side placeholder
```

## Rules

- Put emulator runtime details in the matching platform directory.
- Keep launcher scanning, installed-App discovery and external Intent handling outside this module.
- Reuse `common/` for menus, save-state structure and touch controls.
- Do not create a different visual language for each internal emulator.
- All visible text must use the shared i18n JSON system.
- Preserve separate emulator processes and their exit/language behavior.

## Current routing

| Platform | Internal marker | Activity/core |
|---|---|---|
| GBA | `internal:gba` | `gba/InternalGbaActivity` + mGBA |
| GB/GBC | `internal:gbc` | `gba/InternalGbaActivity` + mGBA |
| FC/NES | `internal:fc` | `fc/InternalFcActivity` + Nestopia |
| SFC/SNES | `internal:sfc` | `fc/InternalFcActivity` SFC mode + Snes9x |
| MD/Genesis | `internal:md` | `md/InternalMdActivity` + Genesis Plus GX |
| PS1 | `internal:ps1` | `ps1/InternalPs1Activity` + PCSX-ReARMed |
| N64 | `internal:n64` | `n64/InternalN64Activity` + Mupen64Plus-Next |
| Arcade | `internal:arcade` | `arcade/InternalArcadeActivity` + MAME 2003-Plus |

PSP and Switch currently use external emulator Apps only. PS1 uses HLE BIOS and
does not bundle Sony firmware.

See `docs/ARCHITECTURE.md` for active paths and legacy duplicate-file warnings.
