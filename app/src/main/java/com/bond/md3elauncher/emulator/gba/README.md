# GBA / GB / GBC Runtime

Active files:

```text
InternalGbaActivity.kt   # Lifecycle, core startup, ROM preparation, save/load and cheats
GbaTouchControlsView.kt  # Touch controls, Canvas menus and controller interaction
GbaModels.kt             # Runtime models, menu state and control definitions
GbaNativeCheatBridge.kt  # Future native backend bridge; not currently active
```

## Supported internal input

- GBA: `.gba`, `.agb`, supported `.zip` contents.
- GB/GBC: `.gb`, `.gbc`, `.sgb`, supported `.zip` contents.
- `.7z` is scanned by the launcher but not extracted here.

## Cheat contract

The stable implementation uses one libretro slot per custom cheat:

```text
slot 0 = cheat 1
slot 1 = cheat 2
slot 2 = cheat 3
```

Multiple lines belonging to one cheat may still be combined for that single slot.

Disabling an active cheat intentionally follows:

```text
quick save → restart game/core → automatic quick load
```

Do not replace this with reset-only cleanup without explicit testing. Some patch-style cheats remain active after a simple reset.

## UI contract

Use `emulator/common/` for shared menu order, save slots and GBA-style touch layout. Visible text must come from i18n JSON and Canvas text must use fitted/bounded rendering.
