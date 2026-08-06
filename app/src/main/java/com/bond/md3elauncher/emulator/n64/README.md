# N64 Runtime

Current configuration:

- Platform: `PlatformKind.N64`
- Internal marker: `internal:n64`
- Cores: `libmupen64plus_next_gles2_libretro_android.so` and
  `libmupen64plus_next_gles3_libretro_android.so`
- Runtime host: `emulator/n64/InternalN64Activity.kt`
- Process: `:internal_n64`
- Scan types: `.n64`, `.v64`, `.z64`, `.bin`, `.zip`, `.7z`

The runtime checks the device's required OpenGL ES version and selects the
matching GLES2/GLES3 core. Bundled N64 cores currently cover `arm64-v8a` and
`armeabi-v7a`; x86/x86_64 devices should use an external emulator.

Before launch, GameHub recognizes the ROM by its header rather than trusting
the extension. Big-endian, byte-swapped, little-endian and 512-byte
copier-header dumps are normalized to an ASCII-path `.z64` cache file. This is
intended to keep translated, patched and incorrectly named dumps compatible
without accepting unrelated `.bin` disc images as N64 games.

N64 reuses the common five-slot plus quick-save menu and keeps reset, restart
and exit as distinct actions. Touch controls provide a true analog left stick,
a right analog C-button stick, digital D-pad, A/B, C-Up/C-Down, Z, L and R.

`.7z` files are scanner-visible but must be extracted before internal launch.
The project does not bundle ROMs, 64DD media or other copyrighted game data.
