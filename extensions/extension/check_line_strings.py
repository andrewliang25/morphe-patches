#!/usr/bin/env python3
"""Checks that every English text that LineSettingsScreen shows has an entry in LineStrings.

A missing or mistyped entry does not fail the build. The screen just shows English, so run this
after a change to either file: python3 extensions/extension/check_line_strings.py
"""
import re
from pathlib import Path

SRC = Path(__file__).parent / "src/main/java/app/andrewliang/extension"
# Texts that stay English in every language: names and the legal notice.
NAMES = {"Andrew's Patches", "GNU General Public License v3.0"}


def strings(code):
    """Each Java string expression in code, with "a" + "b" joined into one string."""
    joined = re.sub(r'"\s*\+\s*"', "", code)
    return [s.encode().decode("unicode_escape").encode("latin-1").decode("utf-8")
            for s in re.findall(r'"((?:[^"\\]|\\.)*)"', joined)]


table = (SRC / "LineStrings.java").read_text(encoding="utf-8")
keys = {strings(m)[0] for m in re.findall(r'put\(((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+),', table)}
screen = (SRC / "LineSettingsScreen.java").read_text(encoding="utf-8")
# Every string in the methods that build the screen is visible text.
body = screen[screen.index("private static void addSwitches"):screen.index("/** A back arrow")]
shown = [body] + re.findall(r'LineStrings\.get\(activity,\s*("(?:[^"\\]|\\.)*")', screen)
texts = {t for s in shown for t in strings(s)}
missing = sorted(texts - keys - NAMES)
assert len(texts) > 50, f"found only {len(texts)} texts; the pattern no longer matches the screen"
assert not missing, "No LineStrings entry for:\n  " + "\n  ".join(missing)
print(f"OK: {len(texts)} texts, all translated or names")
