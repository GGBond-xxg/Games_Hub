# Arcade Runtime

Current configuration:

- Platform: `PlatformKind.ARCADE`
- Internal marker: `internal:arcade`
- Core: `libmame2003_plus_libretro_android.so`
- Runtime host: `emulator/arcade/InternalArcadeActivity.kt`
- Process: `:internal_arcade`
- Scan type: `.zip`

The first phase targets MAME 2003-Plus full non-merged romsets. GameHub copies
the complete ZIP without extracting or renaming its internal files. This is
important because arcade cores identify games by exact ROM filenames and
checksums.

Split sets, parent/BIOS dependencies and CHD games are not promised in the
first phase because SAF content is copied into an isolated cache directory.
Use a full non-merged set or select an external emulator when companion files
are required.

Arcade reuses the common five-slot plus quick-save menu. Save states are
game-dependent in MAME 2003-Plus. Touch buttons are labeled 1–6, with COIN,
START, D-pad and the existing quick actions.

MAME 2003-Plus uses the classic MAME non-commercial license. Its binary must
not be used in a commercial distribution.
