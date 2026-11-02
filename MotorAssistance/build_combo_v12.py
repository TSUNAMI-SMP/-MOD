#!/usr/bin/env python3
"""Publish v12 by changing ONLY the slot-three special attack timing in v11.

v11 remains the base, preserving its fast slot-7 right/G/right combo, Mod Menu
toggle, Cloth Config compatibility, and all other attack behavior.
"""
from pathlib import Path
from tempfile import TemporaryDirectory
from zipfile import ZipFile, ZIP_DEFLATED
import subprocess

repo=Path.cwd()
source=repo/'MotorAssistance/motorassistancemod-fabric-1.21.11-pvp.23-unified-combo-v11.jar'
dest=repo/'MotorAssistance/motorassistancemod-fabric-1.21.11-pvp.23-unified-combo-v12.jar'
patcher=repo/'MotorAssistance/patch-src/AdjustSlotThreeSwapOdds.java'
target='dev/gallon/motorassistance/fabric/combat/AutoAttackController.class'
owner='dev.gallon.motorassistance.fabric.combat.AutoAttackController'
assert source.is_file() and patcher.is_file()

with TemporaryDirectory() as temp:
    tmp=Path(temp)
    classes=tmp/'classes'
    classes.mkdir()
    raw=tmp/'AutoAttackController-original.class'
    patched=tmp/'AutoAttackController-patched.class'
    with ZipFile(source) as original:
        assert original.testzip() is None
        raw.write_bytes(original.read(target))

    subprocess.run(['javac',
        '--add-exports','java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED',
        '-d',str(classes),str(patcher)],check=True)
    subprocess.run(['java',
        '--add-exports','java.base/jdk.internal.org.objectweb.asm=ALL-UNNAMED',
        '-cp',str(classes),
        'dev.gallon.motorassistance.build.AdjustSlotThreeSwapOdds',
        str(raw),str(patched)],check=True)

    with ZipFile(source) as old, ZipFile(dest,'w',compression=ZIP_DEFLATED) as output:
        for info in old.infolist():
            data=patched.read_bytes() if info.filename==target else old.read(info.filename)
            output.writestr(info,data)

with ZipFile(source) as old,ZipFile(dest) as new:
    assert new.testzip() is None
    assert set(new.namelist())==set(old.namelist())
    assert len(new.namelist())==len(set(new.namelist()))
    changes=[p for p in old.namelist() if old.read(p)!=new.read(p)]
    assert changes==[target], f'Unexpected JAR modifications: {changes}'
    # All previous bundled features are still present, unchanged.
    assert 'dev/gallon/motorassistance/fabric/combat/ComboSequencer.class' in new.namelist()
    assert new.read('dev/gallon/motorassistance/fabric/combat/ComboSequencer.class')==old.read('dev/gallon/motorassistance/fabric/combat/ComboSequencer.class')
    assert new.read('dev/gallon/motorassistance/fabric/config/TheModConfig.class')==old.read('dev/gallon/motorassistance/fabric/config/TheModConfig.class')

old_v=subprocess.check_output(['javap','-p','-c','-classpath',str(source),owner],text=True)
new_v=subprocess.check_output(['javap','-p','-c','-classpath',str(dest),owner],text=True)
def special_method(s):
    return s.split('private boolean handleSpearMaceSlotAttack(',1)[1].split(
        'private void updateSlotSixKeyLatch(',1)[0]
original_special=special_method(old_v)
modified_special=special_method(new_v)
assert 'Math.min:(II)I' not in original_special
assert 'Math.min:(II)I' in modified_special
assert 'ThreadLocalRandom.nextInt:(I)I' in modified_special
assert 'lmul' in modified_special
assert original_special.count('randomSwapAttackOffsetMs:()J') == 1
assert modified_special.count('randomSwapAttackOffsetMs:()J') == 1
# Three call sites continue to exist; only slot-3/R gets a multiplier.
assert new_v.count('randomSwapAttackOffsetMs:()J')==old_v.count('randomSwapAttackOffsetMs:()J')
for name in ('private void queueAttack(', 'private void queueAxeThenMaceAttacks('):
    assert name in new_v
assert len([x for x in new_v.split('Math.min:(II)I')]) == 2
p_old=70/101
p_new=0.15+0.85*p_old
assert 0.73 < p_new < 0.75
print(f'PASS: zero-delay chance {p_old:.1%} -> {p_new:.1%}; one class changed; other macros preserved.')
print(f'Published artifact: {dest} ({dest.stat().st_size:,} bytes)')
