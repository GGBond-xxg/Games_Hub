# PCSX-ReARMed provenance

GameHub uses PCSX-ReARMed for its built-in PS1 runtime.

- Upstream: <https://github.com/libretro/pcsx_rearmed>
- Corresponding source revision: `da2cb8e`
- Source revision URL: <https://github.com/libretro/pcsx_rearmed/tree/da2cb8e>
- Core-reported version: `r26 da2cb8e`
- Binary source: <https://buildbot.libretro.com/nightly/android/latest/>
- Downloaded: 2026-08-06
- License: GPL-2.0; see `COPYING`

Packaged binary hashes:

| ABI | SHA-256 |
|---|---|
| `arm64-v8a` | `34D7AED4C77DDC9CEBFB0815FE2F26A6ACDC27E4A55C8658E708B4FEB6648BEA` |
| `armeabi-v7a` | `20317C0C374CB9BA3D491C8C28416DA61E9B798405AA2601221BBC678F88006D` |
| `x86` | `D6E83163FB3A448E60D98CED6ECB2E2641B557EB4CD0139AEB6A5B0A260ED23F` |
| `x86_64` | `4BBAF8B294760DD3BF6F625DB4C1AFA0BDB82567BF302741B3F0A46422875608` |

The core contains HLE BIOS support. GameHub forces HLE mode and does not bundle
Sony BIOS files. ROMs, BIOS files and game assets are not part of this
third-party directory.

Redistributors must comply with GPL-2.0 and make the corresponding source
available. The project-level MIT License does not replace this core license.
