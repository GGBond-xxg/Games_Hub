# Third-Party Notices

GameHub uses LibretroDroid and bundles prebuilt libretro core binaries under `app/src/main/jniLibs/`.

The top-level MIT License covers only GameHub-authored code and documentation.
Every dependency and emulator core remains under its own license. A person who
modifies or redistributes GameHub is responsible for complying with those
licenses; see `DISCLAIMER.md`.

## Active runtime cores

- **mGBA**: GBA and GB/GBC internal emulation.
- **Nestopia**: FC/NES internal emulation. The current package contains the Nestopia binary only for `arm64-v8a`.
- **Snes9x**: SFC/SNES internal emulation.
- **Genesis Plus GX**: MD/Genesis internal emulation. Four Android ABI binaries
  and their SHA-256 hashes are documented in
  `third_party/genesis_plus_gx/README.md`. This core uses a non-commercial
  license; do not include it in a commercial distribution without replacing it
  or obtaining appropriate permission.
- **PCSX-ReARMed**: PS1 internal emulation. Four Android ABI binaries report
  version `r26 da2cb8e`; source revision, hashes and GPL-2.0 text are retained
  under `third_party/pcsx_rearmed/`.
- **Mupen64Plus-Next**: N64 internal emulation. GLES2/GLES3 Android binaries
  for both ARM ABIs, their hashes, origin and upstream GPL-2.0 license text are
  retained under `third_party/mupen64plus_next/`.
- **MAME 2003-Plus**: arcade internal emulation. Android ARM binaries, hashes,
  origin and the classic MAME non-commercial license are retained under
  `third_party/mame2003_plus/`. The bundled core must not be sold or included
  in a commercial distribution.

## Additional bundled binaries

The source package also currently contains FCEUmm binaries for multiple ABIs and one arm64 Mesen binary from earlier FC/NES experiments. Current FC/NES routing selects Nestopia, but Gradle may still package unused files found in `jniLibs`.

Before public distribution, perform a dedicated native-library cleanup and verify that removing unused cores does not affect supported devices.

## License obligations

Licenses and redistribution requirements must be reviewed for every shipped binary and dependency, including:

- LibretroDroid;
- mGBA libretro core;
- Nestopia libretro core;
- Snes9x libretro core;
- Genesis Plus GX libretro core;
- PCSX-ReARMed libretro core;
- Mupen64Plus-Next libretro core;
- MAME 2003-Plus libretro core;
- any FCEUmm or Mesen binary that remains in the final APK.

A historical GPL notice from the Lemuroid core package is retained at:

```text
third_party/lemuroid/COPYING
```

This file is not a substitute for a full release license audit. Before publishing source or APK files, preserve required copyright notices, provide source/offer information where required, and confirm that the exact binary versions and origins are documented.
