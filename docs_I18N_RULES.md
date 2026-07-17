# GameHub I18N Rules

1. All user visible text must use assets/i18n JSON.
2. Languages: en.json, zh.json, zh-Hant.json.
3. No new hardcoded UI text.
4. All buttons use maxLines and ellipsis.
5. Emulator menus and virtual controls must use common i18n keys.


## v0.1.82 i18n emulator cleanup

Built-in emulator visible text must not be hardcoded in Kotlin. GBA/GB/GBC and FC/NES menu labels, save-state labels, virtual button editor text, and toast messages are now routed through assets/i18n/en.json, zh.json, and zh-Hant.json. Chinese aliases inside parser code may remain only as accepted input aliases and must not be displayed directly.

## v0.1.85 Cross-process language rule

Built-in emulator activities run in separate Android processes. Do not rely only on SharedPreferences for language state. `I18n.savedLanguageMode()` reads `filesDir/i18n_language_mode.txt` first, then falls back to SharedPreferences. `I18n.setLanguageOverride()` must update both.

When exiting an internal emulator, close the emulator process as well. Otherwise old process-level language/cache state may remain when the user re-enters a game.

## v0.1.87 Branding Rule

- User-visible app brand is `GameHub`.
- Do not add any legacy app-name user-visible strings.
- Keep Android package id `com.bond.md3elauncher` unless a future release explicitly migrates package identity.

## v0.1.91 Launcher layout text

Launcher list/grid switch labels must use `launcher.layout.list` and `launcher.layout.grid` in all three JSON files. Do not hardcode visible layout labels in Compose UI.
## v0.1.92 Dual artwork editor text

The dual-image editor must use these keys in all three JSON files: `edit.preview_image`, `edit.grid_image`, `edit.search_preview_image`, `edit.search_grid_image`, `edit.pick_preview_image`, `edit.pick_grid_image`, and `edit.dual_image_hint`. The bottom layout controls remain icon-only, but their accessibility descriptions must use `launcher.layout.list` and `launcher.layout.grid`.
## v0.1.93 Compact artwork editor text

The compact artwork cards use `edit.preview_usage`, `edit.grid_usage`, `edit.choose_online`, and `edit.choose_device` in all three JSON files. The full slot-specific keys from v0.1.92 remain the accessibility labels and scraper-page titles.

