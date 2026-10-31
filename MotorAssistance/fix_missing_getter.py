#!/usr/bin/env python3
"""Repair the removed spear-mace getter dependency in the unified 1.21.11 mod.

Keeps the old 4.0-block targeting range on slot 6 (zero-indexed slot 5).
"""
import sys
from pathlib import Path
from zipfile import ZipFile

source, output = map(Path, sys.argv[1:3])
klass = "dev/gallon/motorassistance/common/services/MotorAssistanceService.class"
old = bytes.fromhex("2a b4 00 41 b6 01 1b")
new = bytes.fromhex("04 00 00 00 00 00 00")
with ZipFile(source) as z, ZipFile(output,"w") as out:
    found = False
    for item in z.infolist():
        raw = z.read(item.filename)
        if item.filename == klass:
            if raw.count(old) != 1:
                raise RuntimeError("unexpected bytecode: getter reference not found exactly once")
            raw = raw.replace(old,new,1)
            found = True
        out.writestr(item,raw)
    if not found:
        raise RuntimeError("service class not found")
print(f"Created {output} ({output.stat().st_size} bytes)")