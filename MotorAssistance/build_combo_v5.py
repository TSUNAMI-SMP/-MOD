#!/usr/bin/env python3
"""Build the 1.21.11 MotorAssistance combo JAR in CI with Java 21.
Compilation stubs are never bundled into the published JAR.
"""
from pathlib import Path
import json, subprocess, urllib.request
from tempfile import TemporaryDirectory
from zipfile import ZipFile, ZIP_DEFLATED

repo=Path.cwd()
src_jar=repo/'MotorAssistance/motorassistancemod-fabric-1.21.11-pvp.23-unified.jar'
dest_jar=repo/'MotorAssistance/motorassistancemod-fabric-1.21.11-pvp.23-unified-combo-v5.jar'
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
import dev.gallon.motorassistance.common.mixins.ComboClientInvoker;
import java.util.*;
public class class_310 implements ComboClientInvoker {
 public class_746 field_1724=new class_746();
 public class_638 field_1687=new class_638();
 public class_437 field_1755;
 public class_309 field_1774=new class_309();
 public class_315 field_1690=new class_315();
 public static final class_310 INSTANCE=new class_310();
 public final List<Integer> clickSlots=new ArrayList<>();
 public final List<String> syntheticKeys=new ArrayList<>();
 public static class_310 method_1551(){return INSTANCE;}
 public class_1041 method_22683(){return new class_1041();}
 public void motorassistancemod$useItem(){clickSlots.add(field_1724.method_31548().method_67532());}
}
''',
'net/minecraft/class_746.java': '''package net.minecraft;
public class class_746 {
 private final class_1661 inventory=new class_1661();
 public class_1661 method_31548(){return inventory;}
}
''',
'net/minecraft/class_1661.java': '''package net.minecraft;
public class class_1661 {
 private int selectedSlot;
 public int method_67532(){return selectedSlot;}
 public void method_61496(int slot){selectedSlot=slot;}
}
''',
'net/minecraft/class_315.java': '''package net.minecraft;
public class class_315 {
 public class_304[] field_1852=new class_304[9];
 public class_315(){field_1852[1]=new class_304("key.keyboard.g");}
}
''',
'net/minecraft/class_638.java': '''package net.minecraft;
public class class_638 {}
''',
'net/minecraft/class_437.java': '''package net.minecraft;
public class class_437 {}
''',
'net/minecraft/class_1041.java': '''package net.minecraft;
public class class_1041 { public long method_4490(){return 1L;} }
''',
'net/minecraft/class_304.java': '''package net.minecraft;
public class class_304 {
 private final String key;
 public class_304(String key){this.key=key;}
 public static class_304 method_65807(String id){return new class_304("key.keyboard.v");}
 public boolean method_1434(){return true;}
 public String method_1428(){return key;}
}
''',
'net/minecraft/class_309.java': '''package net.minecraft;
import dev.gallon.motorassistance.common.mixins.ComboKeyboardInvoker;
public class class_309 implements ComboKeyboardInvoker {
 public void motorassistancemod$syntheticKey(long window,int key,int scan,int action,int mods){
  class_310 client=class_310.method_1551();
  client.syntheticKeys.add(key+":"+action);
  if(key==71 && action==1)client.field_1724.method_31548().method_61496(1);
 }
}
''',
'org/spongepowered/asm/mixin/Mixin.java': '''package org.spongepowered.asm.mixin;
import java.lang.annotation.*;
@Retention(RetentionPolicy.CLASS) @Target(ElementType.TYPE)
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
 main.write_bytes((repo/'MotorAssistance/combo-src/ComboSequencerV5.java').read_bytes())
 test=srcdir/'test/ComboSequencerTest.java'
 test.parent.mkdir(parents=True,exist_ok=True)
 test.write_bytes((repo/'MotorAssistance/combo-src/ComboSequencerTestV5.java').read_bytes())
 classes.mkdir()
 subprocess.run(['javac','--release','21','-d',str(classes),*map(str,srcdir.rglob('*.java'))],check=True)
 subprocess.run(['java','-cp',str(classes),'test.ComboSequencerTest'],check=True)
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
# Verify the actual Minecraft 1.21.11 intermediary field descriptors against
# Fabric's version-pinned Yarn source, not the compile-time skeletons.
url='https://raw.githubusercontent.com/FabricMC/yarn/1.21.11/mappings/net/minecraft/client/MinecraftClient.mapping'
with urllib.request.urlopen(url,timeout=30) as response:
 mapping=response.read().decode('utf-8')
expected={
 'field_1724':('player','class_746'),
 'field_1687':('world','class_638'),
 'field_1755':('currentScreen','class_437'),
 'field_1774':('keyboard','class_309'),
}
for field,(named,typ) in expected.items():
 assert f'FIELD {field} {named} Lnet/minecraft/{typ};' in mapping, (field,typ)
seq=subprocess.check_output(['javap','-c','-p','-classpath',str(dest_jar),
    'dev.gallon.motorassistance.fabric.combat.ComboSequencer'],text=True)
for field,(named,typ) in expected.items():
 assert f'Field net/minecraft/class_310.{field}:Lnet/minecraft/{typ};' in seq, (field,typ)
# Also validate every additional mapping accessed by the new slot-checking code.
yarn_files={
 'player':'mappings/net/minecraft/entity/player/PlayerEntity.mapping',
 'inventory':'mappings/net/minecraft/entity/player/PlayerInventory.mapping',
 'options':'mappings/net/minecraft/client/option/GameOptions.mapping',
 'keybinding':'mappings/net/minecraft/client/option/KeyBinding.mapping',
}
yarn={}
for name,path in yarn_files.items():
 with urllib.request.urlopen('https://raw.githubusercontent.com/FabricMC/yarn/1.21.11/'+path,timeout=30) as response:
  yarn[name]=response.read().decode('utf-8')
assert 'METHOD method_31548 getInventory ()Lnet/minecraft/class_1661;' in yarn['player']
assert 'METHOD method_67532 getSelectedSlot ()I' in yarn['inventory']
assert 'FIELD field_1690 options Lnet/minecraft/class_315;' in mapping
assert 'FIELD field_1852 hotbarKeys [Lnet/minecraft/class_304;' in yarn['options']
assert 'METHOD method_1428 getBoundKeyTranslationKey ()Ljava/lang/String;' in yarn['keybinding']
expected_refs=(
 'Field net/minecraft/class_310.field_1690:Lnet/minecraft/class_315;',
 'Field net/minecraft/class_315.field_1852:[Lnet/minecraft/class_304;',
 'Method net/minecraft/class_746.method_31548:()Lnet/minecraft/class_1661;',
 'Method net/minecraft/class_1661.method_67532:()I',
 'Method net/minecraft/class_304.method_1428:()Ljava/lang/String;',
)
for ref in expected_refs:
 assert ref in seq,ref
print('Verified client, inventory, GameOptions, and KeyBinding references using Yarn 1.21.11.')

with ZipFile(dest_jar) as z:
 assert z.testzip() is None
 assert len(z.namelist())==len(set(z.namelist()))
 assert len(json.loads(z.read('motorassistancemod.mixins.json'))['mixins'])==9
 assert all(name in z.namelist() for name in added)
# Mixin's own @Mixin meta-annotation has CLASS retention, meaning it must be
# RuntimeInvisibleAnnotations in bytecode. In v2 it was erroneously RUNTIME,
# leading to InvalidMixinException: missing an @Mixin annotation at startup.
for class_name in (
    'dev.gallon.motorassistance.common.mixins.ComboKeyboardMixin',
    'dev.gallon.motorassistance.common.mixins.ComboClientMixin',
    'dev.gallon.motorassistance.common.mixins.ComboClientInvoker',
    'dev.gallon.motorassistance.common.mixins.ComboKeyboardInvoker',
):
    verbose = subprocess.check_output(
        ['javap', '-v', '-p', '-classpath', str(dest_jar), class_name],
        text=True,
    )
    assert 'RuntimeInvisibleAnnotations:' in verbose
    class_annotation = verbose.rsplit('RuntimeInvisibleAnnotations:', 1)[-1]
    assert 'org.spongepowered.asm.mixin.Mixin(' in class_annotation, class_name
print(f'Wrote {dest_jar} ({dest_jar.stat().st_size} bytes); Mixin retention, slot mapping, and simulated key flow verified.')
