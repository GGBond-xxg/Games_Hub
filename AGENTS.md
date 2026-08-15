# GameHub Codex Instructions

## Start here

Before editing code:

1. Read `README.md`.
2. Read `docs/ARCHITECTURE.md`.
3. Read only the subsystem README relevant to the task.
4. Read `docs/I18N.md` for any user-visible UI change.
5. Do not load old version history unless the task requires it.

Current release is `1.0.1` (`versionCode = 101`).

## Build and checks

Use the Gradle wrapper:

```bash
./gradlew clean assembleDebug
```

Windows:

```powershell
.\gradlew.bat clean assembleDebug
```

For UI text changes, also run:

```bash
python scripts/check_i18n_keys.py
python scripts/check_i18n_hardcoded_text.py
```

The hardcoded-text script is advisory because legacy inactive files still contain old strings. New or modified active UI code must not introduce hardcoded visible text.

## Non-negotiable rules

- Make the smallest change that solves the requested problem.
- Do not redesign unrelated screens or remove existing actions.
- Preserve the launcher’s right-side preview when changing list/grid content.
- Grid layout remains capped at four columns.
- Preserve `A` launch, `B` favorite/add, `L3` move up and `R3` move down behavior.
- Preview artwork and grid artwork are separate fields. Preserve legacy artwork migration.
- Do not change `applicationId = "com.bond.md3elauncher"` without an explicit migration request.
- Do not add ROMs, BIOS, firmware, keys or copyrighted commercial assets.

## Active code paths

Use these paths first:

- Launcher entry: `MainActivity.kt`, `ui/LauncherApp.kt`, `ui/BeaconScreens.kt`, `ui/BeaconChrome.kt`.
- Edit page: `ui/EditItemDialog.kt`.
- Settings: `ui/SettingsScreens.kt`.
- Local state: `data/LauncherStore.kt`.
- Platform models: `data/Models.kt`.
- ROM scanning: `io/RomScanner.kt`.
- External emulator launch: `system/ExternalLauncher.kt`.
- Internal emulator registry: `emulator/InternalEmulators.kt`.
- GBA/GB/GBC: `emulator/gba/`.
- FC/NES and current SFC runtime host: `emulator/fc/`.
- Shared emulator UI: `emulator/common/`.
- Internationalization: `i18n/I18n.kt` and `assets/i18n/*.json`.

The similarly named root-package files listed in `docs/ARCHITECTURE.md` are legacy duplicates. Do not implement new work there.

## UI and i18n

- All new visible strings must exist in `en.json`, `zh.json` and `zh-Hant.json` with identical keys.
- Read text through `I18n.t(...)`, `I18n.short(...)` or the existing local translation helpers.
- Buttons and one-line labels need `maxLines = 1` and ellipsis where space can be constrained.
- Explanatory text should use bounded lines and ellipsis or scrolling.
- Canvas emulator text must use fitted text helpers; do not draw long raw strings.
- Non-Chinese launcher footer hints remain compact/icon-oriented.

## Internal emulator contracts

- Reuse `emulator/common/` before adding platform-specific UI.
- Keep 5 normal save slots plus quick save.
- Keep reset, restart and exit as distinct actions.
- Normal emulator exit must close its separate emulator process.
- GBA/GB/GBC run in `:internal_gba`; FC/NES/SFC run in `:internal_fc`.
- Language changes must remain visible across those processes.

GBA cheat behavior is intentionally conservative:

- One custom cheat equals one libretro slot.
- Disabling an active cheat uses quick-save, game restart and automatic quick-load.
- Do not replace this with a simpler reset-only path unless the user explicitly requests and validates it.

## Validation expectations

For every code change:

1. Compile the project.
2. Check the modified flow for phone-width and tablet-width landscape layouts when relevant.
3. Confirm no existing controller action disappeared.
4. Confirm all three i18n JSON files stay key-synchronized.
5. Report build failures honestly and include the exact failing task/error.

Do not update versionCode/versionName for every small edit. Change versions only when the user asks for a release/version bump or the task is explicitly recorded as a new version.
