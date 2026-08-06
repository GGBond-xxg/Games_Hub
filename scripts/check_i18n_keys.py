#!/usr/bin/env python3
"""Verify that every GameHub i18n JSON file has the same key set."""

from __future__ import annotations

import json
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]
I18N_DIR = ROOT / "app" / "src" / "main" / "assets" / "i18n"
REQUIRED = ("en.json", "zh.json", "zh-Hant.json")


def load_keys(name: str) -> set[str]:
    path = I18N_DIR / name
    if not path.is_file():
        raise FileNotFoundError(f"Missing i18n file: {path.relative_to(ROOT)}")
    with path.open("r", encoding="utf-8") as handle:
        data = json.load(handle)
    if not isinstance(data, dict):
        raise ValueError(f"Expected a JSON object: {path.relative_to(ROOT)}")
    return set(data)


def main() -> int:
    try:
        key_sets = {name: load_keys(name) for name in REQUIRED}
    except (OSError, ValueError, json.JSONDecodeError) as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 1

    baseline_name = REQUIRED[0]
    baseline = key_sets[baseline_name]
    failed = False

    for name in REQUIRED[1:]:
        missing = sorted(baseline - key_sets[name])
        extra = sorted(key_sets[name] - baseline)
        if missing or extra:
            failed = True
            print(f"{name} differs from {baseline_name}:")
            if missing:
                print("  Missing: " + ", ".join(missing))
            if extra:
                print("  Extra: " + ", ".join(extra))

    if failed:
        return 1

    print(f"OK: {len(baseline)} keys are synchronized across {', '.join(REQUIRED)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
