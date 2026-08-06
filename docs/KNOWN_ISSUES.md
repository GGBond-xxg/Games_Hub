# Known Issues and Limits

## Internal ROM loading

- `.7z` files are recognized during scanning but are not extracted by current internal emulators. Users must extract them first or select a compatible external emulator.
- `.fds` may require an FDS BIOS; the project does not bundle one.
- Built-in MD/Genesis currently targets cartridge games only; Sega CD and 32X are not included.
- Built-in PS1 currently supports single-file `.chd`, `.pbp`, `.iso` and `.bin` images. Multi-track `.cue` and multi-disc `.m3u` sets require a future grouped-file importer.
- PCSX-ReARMed uses its HLE BIOS because GameHub does not bundle Sony firmware. Some games may require a user-owned BIOS in a compatible external emulator for best compatibility.
- Built-in N64 supports cartridge dumps and ordinary ZIP archives. Nintendo 64DD media and Transfer Pak content are not part of the first integration.
- Built-in arcade targets MAME 2003-Plus full non-merged `.zip` romsets. Split sets, parent/BIOS dependencies and CHD games may fail when their companion files are not embedded in the selected set.
- Modified or unusual ROMs can still expose core compatibility differences even when their extension is supported.

## External emulator launching

Android emulator Apps do not share one standard ROM-launch Intent. `ExternalLauncher.kt` tries generic and app-specific approaches, but some versions can only open their home/library screen.

- Nes.emu and common RetroArch paths are the primary FC/NES fallbacks.
- My OldBoy! direct launch depends on the installed version.
- PSP remains external-emulator based.
- Switch support is launcher-side only and does not include keys or firmware.
- MD.emu and RetroArch remain optional MD/Genesis fallbacks.
- DuckStation, ePSXe, FPse and RetroArch remain optional PS1 fallbacks.
- M64Plus FZ, Mupen64Plus and RetroArch remain optional N64 fallbacks.
- MAME4droid, RetroArch and FinalBurn remain optional arcade fallbacks.

## GBA cheats

- Disabling an active cheat restarts the game and restores a quick save. This is deliberate because reset-only removal is unreliable for some patches.
- Some cheat combinations, such as walk-through-walls and shiny-related codes, may conflict depending on ROM/core behavior.
- The native mGBA CheatManager bridge is only a future direction, not an active backend.

## Native ABI coverage

The active FC/NES runtime requires Nestopia, but the current source package contains `libnestopia_libretro_android.so` only for `arm64-v8a`. Other ABIs should not be claimed as fully supported until equivalent binaries are added or the ABI set is intentionally restricted. Older FCEUmm/Mesen binaries also remain and need a separate cleanup/license audit.

Genesis Plus GX binaries are packaged for `arm64-v8a`, `armeabi-v7a`, `x86` and `x86_64`. Its license is non-commercial; redistribution terms must be reviewed before any commercial release.

Mupen64Plus-Next GLES2/GLES3 binaries are packaged for `arm64-v8a` and
`armeabi-v7a`. The current official Android buildbot does not provide matching
x86/x86_64 binaries, so built-in N64 is intentionally unavailable on those
ABIs; an external emulator remains usable.

MAME 2003-Plus binaries are packaged for `arm64-v8a` and `armeabi-v7a`.
Its classic MAME license is non-commercial and requires source availability;
do not include the bundled core in a commercial distribution.

## Source cleanup debt

Several old duplicate Kotlin files remain in legacy packages. Active paths are listed in `docs/ARCHITECTURE.md`. They should be removed only in a dedicated cleanup with a successful build and emulator smoke tests.

## Distribution policy

`AndroidManifest.xml` currently requests `QUERY_ALL_PACKAGES` to discover launchable Apps and emulators. This is suitable for private/sideload use but requires policy review before publishing through stores with restricted package-visibility rules.

## Testing limits

A successful Gradle build does not validate native core behavior, external emulator Intent compatibility or controller mappings. Changes in those areas require testing on a real Android device with representative ROMs that the tester legally owns.
