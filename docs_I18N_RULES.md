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
