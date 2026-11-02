package dev.gallon.motorassistance.build;

import java.nio.file.Files;
import java.nio.file.Path;
import jdk.internal.org.objectweb.asm.*;

/**
 * Narrow bytecode patch for the slot-3 + hotbar-6 key attack only.
 *
 * Original timing offset: 0..5ms, weighted [70,16,8,4,2,1].
 * 15% of draws are converted to 0ms; remaining 85% retain original distribution.
 * This raises P(0ms) from 70/101 (~69.3%) to ~73.9%, retaining a 26.1% chance
 * of non-zero offsets. All other calls to randomSwapAttackOffsetMs stay untouched.
 *
 * No new control-flow branch or frame is introduced: offset *= min(1, random/15).
 */
public final class AdjustSlotThreeSwapOdds {
    private static final String OWNER =
        "dev/gallon/motorassistance/fabric/combat/AutoAttackController";
    private static final String TARGET = "handleSpearMaceSlotAttack";
    private static final String OFFSET = "randomSwapAttackOffsetMs";
    private static final String RNG = "java/util/concurrent/ThreadLocalRandom";
    private static int targetMethodCount, replacedCallCount;

    private AdjustSlotThreeSwapOdds() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("Input.class Output.class");
        Path source = Path.of(args[0]);
        Path target = Path.of(args[1]);
        ClassReader reader = new ClassReader(Files.readAllBytes(source));
        if (!OWNER.equals(reader.getClassName()))
            throw new IllegalStateException("Wrong class: " + reader.getClassName());
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        ClassVisitor patch = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override public MethodVisitor visitMethod(int access, String name,
                 String descriptor, String signature, String[] exceptions) {
                MethodVisitor base=super.visitMethod(access,name,descriptor,signature,exceptions);
                if (!TARGET.equals(name)) return base;
                if (!"(Lnet/minecraft/class_310;J)Z".equals(descriptor))
                    throw new IllegalStateException("Unexpected special-attack descriptor");
                targetMethodCount++;
                return new MethodVisitor(Opcodes.ASM9,base) {
                    @Override public void visitMethodInsn(int opcode,String owner,
                            String methodName,String methodDescriptor,boolean isInterface) {
                        super.visitMethodInsn(opcode,owner,methodName,methodDescriptor,isInterface);
                        if (opcode==Opcodes.INVOKEVIRTUAL && OWNER.equals(owner)
                            && OFFSET.equals(methodName) && "()J".equals(methodDescriptor)) {
                            replacedCallCount++;
                            // Original long offset is on the operand stack.
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,RNG,"current",
                                "()Ljava/util/concurrent/ThreadLocalRandom;",false);
                            super.visitIntInsn(Opcodes.BIPUSH,100);
                            super.visitMethodInsn(Opcodes.INVOKEVIRTUAL,RNG,"nextInt","(I)I",false);
                            super.visitIntInsn(Opcodes.BIPUSH,15);
                            super.visitInsn(Opcodes.IDIV);  // 0 for 0..14, >=1 otherwise
                            super.visitInsn(Opcodes.ICONST_1);
                            super.visitMethodInsn(Opcodes.INVOKESTATIC,"java/lang/Math",
                                "min","(II)I",false); // clamp to 0 or 1
                            super.visitInsn(Opcodes.I2L);
                            super.visitInsn(Opcodes.LMUL); // long result; no branches
                        }
                    }
                };
            }
        };
        reader.accept(patch,0);
        if (targetMethodCount!=1 || replacedCallCount!=1)
            throw new IllegalStateException("Expected 1 special attack method+call, got "
                +targetMethodCount+"/"+replacedCallCount);
        byte[] bytes=writer.toByteArray();
        // ASM verifies the class is readable after the stack-safe insertion.
        ClassReader verification=new ClassReader(bytes);
        if (!OWNER.equals(verification.getClassName()))
            throw new IllegalStateException("Patched bytecode not readable");
        Files.write(target,bytes);
        System.out.println("PASS: only slot-3 special attack got 15% zero-delay bias.");
    }
}
