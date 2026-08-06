# PS1 Runtime

Current configuration:

- Platform: `PlatformKind.PS1`
- Internal marker: `internal:ps1`
- Core: `libpcsx_rearmed_libretro_android.so`
- Runtime host: `emulator/ps1/InternalPs1Activity.kt`
- Process: `:internal_ps1`
- Scan types: `.chd`, `.pbp`, `.iso`, `.bin`

The first phase supports single-file disc images. `.cue` and `.m3u` are not
advertised until the launcher can copy all referenced tracks as one set.

PCSX-ReARMed runs with its HLE BIOS. GameHub does not bundle Sony BIOS files.
HLE improves legal portability but may be less compatible than a user-supplied
BIOS in an external emulator.

PS1 reuses the common five-slot plus quick-save menu, reset/restart/exit
actions and controller shortcuts. Touch controls use the RetroPad mapping:
Cross=B, Circle=A, Square=Y, Triangle=X, plus L1/R1/L2/R2.
