# FC / NES Runtime

Current internal core: Nestopia (`libnestopia_libretro_android.so`).

```text
InternalFcActivity.kt         # FC/NES runtime; also accepts current SFC mode
FcTouchControlsView.kt        # Shared-style touch controls and Canvas menus
FcModels.kt                   # Runtime/save/control models
FcExternalEmulatorProfiles.kt # External emulator recognition hints
```

Supported FC/NES scan types:

```text
.nes .fds .unf .unif .zip .7z
```

- Ordinary supported ROMs and supported files inside `.zip` can be prepared for the internal core.
- `.7z` requires extraction or an external emulator.
- `.fds` may require a user-provided BIOS; none is bundled.
- ROMs are copied to an ASCII-safe cache path before native loading to reduce failures caused by special filenames.

External launch remains available. Nes.emu and RetroArch are the primary fallback paths; direct launch support varies by installed App version.

All menus and virtual controls must continue to follow `emulator/common/`.
