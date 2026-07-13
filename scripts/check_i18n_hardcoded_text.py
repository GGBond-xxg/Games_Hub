#!/usr/bin/env python3
"""List remaining hard-coded Chinese strings in Kotlin source.

Usage:
  python scripts/check_i18n_hardcoded_text.py

This is a guard for the JSON text migration. New user-facing text should be
placed in app/src/main/assets/i18n/en.json, zh.json and zh-Hant.json, then read
through I18n.t(...).
"""
from __future__ import annotations
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parents[1]
SRC = ROOT / "app" / "src" / "main" / "java"
PATTERN = re.compile(r'"([^"\\]*(?:\\.[^"\\]*)*)"')
HAN = re.compile(r'[\u4e00-\u9fff]')
IGNORE_FILE_PARTS = {
    "I18n.kt",
}

for path in sorted(SRC.rglob("*.kt")):
    if path.name in IGNORE_FILE_PARTS:
        continue
    rel = path.relative_to(ROOT)
    for line_no, line in enumerate(path.read_text(encoding="utf-8", errors="ignore").splitlines(), 1):
        # Fallback Chinese inside I18n.t(...)/tr(...) is allowed by the project rule.
        # The goal is to catch newly hard-coded user-facing text outside i18n calls.
        stripped = line.strip()
        if stripped.startswith("//"):
            continue
        if "I18n.t(" in line or "I18n.short(" in line or "tr(" in line or "trShort(" in line:
            continue
        # Chinese aliases in input parsers are allowed so users can type 快存/快读 etc.
        if "parseTouchMethod" in line or "->" in line and any(alias in line for alias in ["快存", "快读", "快进", "菜单", "退出", "确认", "取消", "开始", "选择"]):
            continue
        for match in PATTERN.finditer(line):
            text = match.group(1)
            if HAN.search(text):
                print(f"{rel}:{line_no}: {text}")
