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

## Historical source-only binaries

Version 1.3.5 removes unused FCEUmm binaries from both released ARM ABIs and the
unused arm64 Mesen binary. Current FC/NES routing continues to use Nestopia;
external RetroArch core selection is unchanged and uses RetroArch's own files.

Historical FCEUmm binaries for x86/x86_64 remain in the source repository but are
not included in the ARM-only release APKs. Existing license texts and provenance
notes are retained. Any future build that packages these source-only binaries
must review and satisfy their redistribution requirements.

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
- historical FCEUmm binaries if a future non-ARM build includes them.

A historical GPL notice from the Lemuroid core package is retained at:

```text
third_party/lemuroid/COPYING
```

This file is not a substitute for a full release license audit. Before publishing source or APK files, preserve required copyright notices, provide source/offer information where required, and confirm that the exact binary versions and origins are documented.

## UI libraries

- **AndroidLiquidGlass / Backdrop 1.0.6**, Copyright Kyant0, Apache License 2.0. Source: https://github.com/Kyant0/AndroidLiquidGlass/tree/1.0.6 . Used for Android glass backdrop blur and lens effects.
- **MIUIX 0.9.4**, Copyright compose-miuix-ui contributors, Apache License 2.0. Source: https://github.com/compose-miuix-ui/miuix/tree/v0.9.4 . Used for HyperOS-style Compose controls and theme tokens.
- These libraries retain their upstream copyright and Apache-2.0 license; the license is included in `app/src/main/assets/legal/APACHE-2.0.txt`. No upstream demo artwork is bundled.
