#!/usr/bin/env python3
"""Build the 1.21.11 MotorAssistance combo JAR in CI with Java 21.
Compilation stubs are never bundled into the published JAR.
"""
from pathlib import Path
import json, subprocess
from tempfile import TemporaryDirectory
from zipfile import ZipFile, ZIP_DEFLATED

repo=Path.cwd()
src_jar=repo/'MotorAssistance/motorassistancemod-fabric-1.21.11-pvp.23-unified.jar'
dest_jar=repo/'MotorAssistance/motorassistancemod-fabric-1.21.11-pvp.23-unified-combo-v2.jar'
java_sources={
'dev/gallon/motorassistance/common/mixins/ComboKeyboardMixin.java': '''package dev.gallon.motorassistance.common.mixins;
import dev.gallon.motorassistance.fabric.combat.ComboSequencer;
import net.minecraft.class_309;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(class_309.class)
public abstract class ComboKeyboardMixin {
 @Inject(method="method_22676(JIIII)V",at=@At("HEAD"),cancellable=true)
 private void motorassistancemod$triggerCombo(long window,int key,int scan,int action,int mods,CallbackInfo ci) {
  if(ComboSequencer.onRawKey(window,key,action))ci.cancel();
 }
}
''',
'dev/gallon/motorassistance/common/mixins/ComboClientMixin.java': '''package dev.gallon.motorassistance.common.mixins;
import dev.gallon.motorassistance.fabric.combat.ComboSequencer;
import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(class_310.class)
public abstract class ComboClientMixin {
 @Inject(method="method_1523(Z)V",at=@At("HEAD"))
 private void motorassistancemod$stepCombo(boolean tick,CallbackInfo ci){
  ComboSequencer.onRender((class_310)(Object)this);
 }
}
''',
'dev/gallon/motorassistance/common/mixins/ComboClientInvoker.java': '''package dev.gallon.motorassistance.common.mixins;
import net.minecraft.class_310;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(class_310.class)
public interface ComboClientInvoker {
 @Invoker("method_1583") void motorassistancemod$useItem();
}
''',
'dev/gallon/motorassistance/common/mixins/ComboKeyboardInvoker.java': '''package dev.gallon.motorassistance.common.mixins;
import net.minecraft.class_309;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(class_309.class)
public interface ComboKeyboardInvoker {
 @Invoker("method_22676") void motorassistancemod$syntheticKey(long window,int key,int scan,int action,int mods);
}
''',
'net/minecraft/class_310.java': '''package net.minecraft;
public class class_310 {
 public Object field_1724,field_1687,field_1755;
 public class_309 field_1774;
 public static class_310 method_1551(){return null;}
 public class_1041 method_22683(){return null;}
}
''',
'net/minecraft/class_1041.java': '''package net.minecraft;
public class class_1041 { public long method_4490(){return 0L;} }
''',
'net/minecraft/class_304.java': '''package net.minecraft;
public class class_304 {
 public static class_304 method_65807(String id){return null;}
 public boolean method_1434(){return false;}
}
''',
'net/minecraft/class_309.java': '''package net.minecraft;
public class class_309 {}
''',
'org/spongepowered/asm/mixin/Mixin.java': '''package org.spongepowered.asm.mixin;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.TYPE)
public @interface Mixin {Class<?>[] value();}
''',
'org/spongepowered/asm/mixin/injection/At.java': '''package org.spongepowered.asm.mixin.injection;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) @Target({ElementType.METHOD,ElementType.ANNOTATION_TYPE})
public @interface At {String value();}
''',
'org/spongepowered/asm/mixin/injection/Inject.java': '''package org.spongepowered.asm.mixin.injection;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface Inject {String method();At at();boolean cancellable() default false;}
''',
'org/spongepowered/asm/mixin/injection/callback/CallbackInfo.java': '''package org.spongepowered.asm.mixin.injection.callback;
public class CallbackInfo { public void cancel(){} }
''',
'org/spongepowered/asm/mixin/gen/Invoker.java': '''package org.spongepowered.asm.mixin.gen;
import java.lang.annotation.*;
@Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
public @interface Invoker {String value();}
'''
}
with TemporaryDirectory() as t:
 base=Path(t);srcdir=base/'src';classes=base/'classes'
 for name,source in java_sources.items():
  path=srcdir/name;path.parent.mkdir(parents=True,exist_ok=True)
  path.write_text(source,encoding='utf8')
 main=srcdir/'dev/gallon/motorassistance/fabric/combat/ComboSequencer.java'
 main.parent.mkdir(parents=True,exist_ok=True)
 main.write_bytes((repo/'MotorAssistance/combo-src/ComboSequencer.java').read_bytes())
 classes.mkdir()
 subprocess.run(['javac','--release','21','-d',str(classes),*map(str,srcdir.rglob('*.java'))],check=True)
 added={p.relative_to(classes).as_posix():p.read_bytes()
        for p in classes.glob('dev/gallon/motorassistance/**/*.class')}
 assert len(added)==5,list(added)
 with ZipFile(src_jar) as original,ZipFile(dest_jar,'w',compression=ZIP_DEFLATED) as result:
  for item in original.infolist():
   b=original.read(item.filename)
   if item.filename=='dev/gallon/motorassistance/common/services/MotorAssistanceService.class':
    old=bytes.fromhex('2a b4 00 41 b6 01 1b')
    assert b.count(old)==1,'Unexpected legacy getter'
    b=b.replace(old,bytes.fromhex('04 00 00 00 00 00 00'),1)
   elif item.filename=='motorassistancemod.mixins.json':
    cfg=json.loads(b)
    for n in ('ComboKeyboardMixin','ComboClientMixin','ComboClientInvoker','ComboKeyboardInvoker'):
     assert n not in cfg['mixins']
     cfg['mixins'].append(n)
    b=json.dumps(cfg,indent=2,ensure_ascii=False).encode('utf8')
   result.writestr(item,b)
  for name,b in added.items():result.writestr(name,b)
with ZipFile(dest_jar) as z:
 assert z.testzip() is None
 assert len(z.namelist())==len(set(z.namelist()))
 assert len(json.loads(z.read('motorassistancemod.mixins.json'))['mixins'])==9
 assert all(name in z.namelist() for name in added)
print(f'Wrote {dest_jar} ({dest_jar.stat().st_size} bytes); verified.')
