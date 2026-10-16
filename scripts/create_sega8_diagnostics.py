"""Generate original Z80 diagnostics in ignored build/verification/v136-probes.

No downloaded/commercial ROM, BIOS or firmware. Red = boot/reset, green = button 1,
blue = button 2, yellow = Pause/Start. The latched RAM value exercises save states.
VDP/input reference: https://www.smspower.org/Development/VDPRegisters
Run from the repository root; generated binaries must never be committed/packaged.
"""
from pathlib import Path
import zipfile


def build_probe(system):
    rom = bytearray(32768)
    rom[:3] = bytes([0xC3, 0, 1])  # JP $0100
    # NMI: preserve AF, latch yellow in RAM, RETN (SMS/SG console Pause).
    rom[0x66:0x70] = bytes.fromhex("f5 3e 03 32 01 c0 f1 ed 45 00")
    code, labels, fixups = bytearray(), {}, []

    def emit(*values):
        code.extend(values)

    def label(name):
        labels[name] = 0x100 + len(code)

    def jump(opcode, name):
        emit(opcode, 0, 0)
        fixups.append((len(code) - 2, name))

    def output(port, value):
        emit(0x3E, value, 0xD3, port)  # LD A,value; OUT (port),A

    def register(index, value):
        output(0xBF, value)
        output(0xBF, 0x80 | index)

    # DI; clear the VDP address latch even after soft reset; initialize work RAM.
    emit(0xF3, 0xDB, 0xBF, 0x31, 0xF0, 0xC7, 0xAF, 0x32, 0, 0xC0, 0x32, 1, 0xC0)
    register(0, 0 if system == "SG1000" else 6)
    register(1, 0x80)  # Blanked display: only the backdrop is drawn.
    register(7, 0)
    label("loop")
    emit(0xDB, 0xDC, 0xE6, 0x10)  # Active-low button 1.
    jump(0xCA, "green")
    emit(0xDB, 0xDC, 0xE6, 0x20)  # Active-low button 2.
    jump(0xCA, "blue")
    if system == "GG":
        emit(0xDB, 0, 0xE6, 0x80)  # Game Gear Start input.
        jump(0xCA, "yellow")
    else:
        emit(0x3A, 1, 0xC0, 0xB7)  # NMI flag.
        jump(0xC2, "yellow")
    jump(0xC3, "draw")
    for name, index in [("green", 1), ("blue", 2), ("yellow", 3)]:
        label(name)
        emit(0x3E, index, 0x32, 0, 0xC0, 0xAF, 0x32, 1, 0xC0)
        jump(0xC3, "draw")
    label("draw")
    emit(0x3A, 0, 0xC0, 0x5F, 0x16, 0, 0x21, 0, 0)
    fixups.append((len(code) - 2, "palette"))
    emit(0x19, 0x7E, 0x47)  # Read the selected color from the ROM palette table.
    if system == "SG1000":
        emit(0x78, 0xD3, 0xBF)
        output(0xBF, 0x87)  # TMS9918 backdrop palette index.
    else:
        output(0xBF, 32 if system == "GG" else 16)
        output(0xBF, 0xC0)
        emit(0x78, 0xD3, 0xBE)  # Sprite palette entry 0 is the blanked backdrop.
        if system == "GG":
            emit(0x3A, 0, 0xC0, 0xFE, 2, 0x3E, 0)
            jump(0xC2, "gg_high")
            emit(0x3E, 15)
            label("gg_high")
            emit(0xD3, 0xBE)  # GG uses a second byte for the blue component.
    jump(0xC3, "loop")
    label("palette")
    palettes = {"SG1000": [6, 2, 4, 10], "GG": [15, 240, 0, 255], "SMS": [3, 12, 48, 15]}
    emit(*palettes[system])
    for position, name in fixups:
        code[position:position + 2] = labels[name].to_bytes(2, "little")
    rom[0x100:0x100 + len(code)] = code
    if system != "SG1000":
        rom[0x7FF0:0x7FF8] = b"TMR SEGA"
        rom[0x7FFF] = 0x6C if system == "GG" else 0x4C
    return rom


if __name__ == "__main__":
    output_dir = Path("build/verification/v136-probes")
    output_dir.mkdir(parents=True, exist_ok=True)
    for system, extension in [("SMS", "sms"), ("GG", "gg"), ("SG1000", "sg")]:
        rom = build_probe(system)
        (output_dir / f"probe.{extension}").write_bytes(rom)
        with zipfile.ZipFile(output_dir / f"probe-{system}.zip", "w") as archive:
            archive.writestr("wrong-platform.md", b"invalid")
            archive.writestr(f"probe.{extension}", rom)
    print("Generated three original 32 KiB diagnostics and ZIPs in the ignored build directory.")
