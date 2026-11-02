package dev.gallon.motorassistance.build;

import java.nio.file.Files;
import java.nio.file.Path;
import jdk.internal.org.objectweb.asm.*;

/**
 * Add a mutable boolean to the already registered Cloth AutoConfig class.
 * The original @Config and collapsed original config group are left intact.
 * The new field appears as a normal toggle in the existing Mod Menu screen.
 */
public class PatchModMenuConfig {
    private static final String OWNER="dev/gallon/motorassistance/fabric/config/TheModConfig";
    private static final String FIELD="slotSevenRightClickComboEnabled";

    public static void main(String[] args) throws Exception {
        Path input=Path.of(args[0]), output=Path.of(args[1]);
        ClassReader reader=new ClassReader(Files.readAllBytes(input));
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);
        ClassVisitor cv=new ClassVisitor(Opcodes.ASM9,writer){
            private boolean sawField=false;
            @Override
            public FieldVisitor visitField(int access,String name,String descriptor,
                String signature,Object value){
                if(FIELD.equals(name)) sawField=true;
                return super.visitField(access,name,descriptor,signature,value);
            }
            @Override
            public MethodVisitor visitMethod(int access,String name,String desc,
                String signature,String[] exceptions){
                MethodVisitor mv=super.visitMethod(access,name,desc,signature,exceptions);
                if(!"<init>".equals(name) || !"()V".equals(desc)) return mv;
                return new MethodVisitor(Opcodes.ASM9,mv){
                    @Override
                    public void visitInsn(int opcode){
                        if(opcode==Opcodes.RETURN){
                            super.visitVarInsn(Opcodes.ALOAD,0);
                            super.visitInsn(Opcodes.ICONST_1); // enabled by default
                            super.visitFieldInsn(Opcodes.PUTFIELD,OWNER,FIELD,"Z");
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
            @Override
            public void visitEnd(){
                if(sawField) throw new IllegalStateException("Config toggle already exists");
                FieldVisitor fv=super.visitField(Opcodes.ACC_PUBLIC,FIELD,"Z",null,null);
                if(fv!=null) fv.visitEnd();
                super.visitEnd();
            }
        };
        reader.accept(cv,0);
        Files.write(output,writer.toByteArray());
        // Validate the generated field descriptor by reading it back through ASM.
        boolean[] found={false};
        new ClassReader(Files.readAllBytes(output)).accept(
            new ClassVisitor(Opcodes.ASM9){
                @Override public FieldVisitor visitField(int access,String name,
                    String descriptor,String signature,Object value){
                    if(FIELD.equals(name) && "Z".equals(descriptor)) found[0]=true;
                    return null;
                }
            },ClassReader.SKIP_CODE|ClassReader.SKIP_DEBUG|ClassReader.SKIP_FRAMES);
        if(!found[0]) throw new IllegalStateException("Patched config field missing");
        System.out.println("PASS: ModMenu AutoConfig boolean field added and verified.");
    }
}
