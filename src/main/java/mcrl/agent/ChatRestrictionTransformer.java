package mcrl.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Finds Minecraft's client-side chat restriction gate by shape, not by name.
 *
 * Two real shapes exist depending on Minecraft's era, both confirmed against real
 * client jars and official mappings (see the project's verification notes):
 *
 * Legacy shape (1.19 - 1.21.11, Forge "Minecraft.getChatStatus()" / Fabric
 * "MinecraftClient.getChatRestriction()"): a single enum with constants ENABLED,
 * DISABLED_BY_OPTIONS, DISABLED_BY_PROFILE, DISABLED_BY_LAUNCHER, and a zero-arg
 * getter returning it. Patched by swapping DISABLED_BY_PROFILE for ENABLED on return.
 *
 * Modern shape (26.1+, unobfuscated): Mojang restructured this into a
 * "ChatAbilities" object built from a set of "ChatRestriction" enum constants
 * (CHAT_AND_COMMANDS_DISABLED_BY_OPTIONS, CHAT_DISABLED_BY_OPTIONS,
 * DISABLED_BY_LAUNCHER, DISABLED_BY_PROFILE - no ENABLED constant at all; "no
 * restriction" is the absence of any). Patched by no-opping the fluent
 * "addRestriction(ChatRestriction) -> same builder type" method whenever it's
 * called with DISABLED_BY_PROFILE, so that reason never gets recorded.
 *
 * Neither strategy hardcodes a class or method name - both match purely on enum
 * constant shape, so the same jar works across every loader (Forge/NeoForge/
 * Fabric/Quilt/vanilla) for whichever era of the game is actually running.
 */
public class ChatRestrictionTransformer implements ClassFileTransformer {

    private static final Set<String> LEGACY_REQUIRED = Set.of(
            "ENABLED", "DISABLED_BY_OPTIONS", "DISABLED_BY_PROFILE", "DISABLED_BY_LAUNCHER");
    private static final String FIX_OWNER = "mcrl/agent/ChatRestrictionFix";

    private final Instrumentation instrumentation;
    private final AtomicReference<String> legacyEnumInternalName = new AtomicReference<>();
    private final AtomicReference<String> modernEnumInternalName = new AtomicReference<>();

    public ChatRestrictionTransformer(Instrumentation instrumentation) {
        this.instrumentation = instrumentation;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                             ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null || classfileBuffer == null) {
            return null;
        }
        try {
            ClassReader reader = new ClassReader(classfileBuffer);

            EnumShape shape = classifyEnum(reader);
            if (shape == EnumShape.LEGACY && legacyEnumInternalName.compareAndSet(null, reader.getClassName())) {
                String enumInternalName = reader.getClassName();
                System.out.println("[mcrl] found legacy chat-restriction enum: " + enumInternalName);
                retransformLaterFor(enumInternalName, loader);
                return null;
            }
            if (shape == EnumShape.MODERN && modernEnumInternalName.compareAndSet(null, reader.getClassName())) {
                String enumInternalName = reader.getClassName();
                System.out.println("[mcrl] found modern chat-restriction enum: " + enumInternalName);
                retransformLaterFor(enumInternalName, loader);
                return null;
            }
            if (shape != EnumShape.NONE) {
                return null;
            }

            String legacyName = legacyEnumInternalName.get();
            if (legacyName != null && hasLegacyGetter(reader, legacyName)) {
                return patchLegacyGetter(reader, legacyName, className);
            }

            String modernName = modernEnumInternalName.get();
            if (modernName != null && hasModernAdder(reader, modernName)) {
                return patchModernAdder(reader, modernName, className, loader);
            }

            return null;
        } catch (Throwable t) {
            System.err.println("[mcrl] failed to inspect " + className);
            t.printStackTrace();
            return null;
        }
    }

    private enum EnumShape { NONE, LEGACY, MODERN }

    private EnumShape classifyEnum(ClassReader reader) {
        if (!"java/lang/Enum".equals(reader.getSuperName())) {
            return EnumShape.NONE;
        }
        String selfDescriptor = "L" + reader.getClassName() + ";";
        Set<String> constants = new HashSet<>();
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor,
                                            String signature, Object value) {
                if ((access & Opcodes.ACC_ENUM) != 0 && selfDescriptor.equals(descriptor)) {
                    constants.add(name);
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

        if (constants.containsAll(LEGACY_REQUIRED)) {
            return EnumShape.LEGACY;
        }
        if (constants.contains("DISABLED_BY_PROFILE") && constants.contains("DISABLED_BY_LAUNCHER")
                && !constants.contains("ENABLED")) {
            return EnumShape.MODERN;
        }
        return EnumShape.NONE;
    }

    // ---- legacy shape: zero-arg getter returning the enum ----

    private boolean hasLegacyGetter(ClassReader reader, String enumInternalName) {
        String targetDescriptor = "()L" + enumInternalName + ";";
        boolean[] found = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                              String signature, String[] exceptions) {
                if (targetDescriptor.equals(descriptor)) {
                    found[0] = true;
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found[0];
    }

    private byte[] patchLegacyGetter(ClassReader reader, String enumInternalName, String className) {
        String targetDescriptor = "()L" + enumInternalName + ";";
        ClassWriter writer = newClassWriter(reader, null);
        boolean[] patched = {false};

        ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                              String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!targetDescriptor.equals(descriptor)) {
                    return mv;
                }
                patched[0] = true;
                System.out.println("[mcrl] patching (legacy) " + className + "#" + name + descriptor);
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.ARETURN) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, FIX_OWNER, "fixReturnValue",
                                    "(Ljava/lang/Object;)Ljava/lang/Object;", false);
                            super.visitTypeInsn(Opcodes.CHECKCAST, enumInternalName);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        };

        reader.accept(classVisitor, 0);
        return patched[0] ? writer.toByteArray() : null;
    }

    // ---- modern shape: fluent "addRestriction(Enum) -> same builder type" ----

    private boolean hasModernAdder(ClassReader reader, String enumInternalName) {
        String targetDescriptor = "(L" + enumInternalName + ";)L" + reader.getClassName() + ";";
        boolean[] found = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                              String signature, String[] exceptions) {
                if (targetDescriptor.equals(descriptor) && (access & Opcodes.ACC_STATIC) == 0) {
                    found[0] = true;
                }
                return null;
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        return found[0];
    }

    private byte[] patchModernAdder(ClassReader reader, String enumInternalName, String className, ClassLoader loader) {
        String targetDescriptor = "(L" + enumInternalName + ";)L" + reader.getClassName() + ";";
        ClassWriter writer = newClassWriter(reader, loader);
        boolean[] patched = {false};

        ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                              String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!targetDescriptor.equals(descriptor) || (access & Opcodes.ACC_STATIC) != 0) {
                    return mv;
                }
                patched[0] = true;
                System.out.println("[mcrl] patching (modern) " + className + "#" + name + descriptor);
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        Label continueLabel = new Label();
                        super.visitVarInsn(Opcodes.ALOAD, 1);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, FIX_OWNER, "isProfileRestriction",
                                "(Ljava/lang/Object;)Z", false);
                        super.visitJumpInsn(Opcodes.IFEQ, continueLabel);
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitInsn(Opcodes.ARETURN);
                        super.visitLabel(continueLabel);
                    }
                };
            }
        };

        reader.accept(classVisitor, 0);
        return patched[0] ? writer.toByteArray() : null;
    }

    private ClassWriter newClassWriter(ClassReader reader, ClassLoader loader) {
        return new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected ClassLoader getClassLoader() {
                return loader != null ? loader : super.getClassLoader();
            }
        };
    }

    /**
     * The enum is usually only resolved (and thus first loaded) the moment the
     * player opens chat for the first time - which can easily be after the class
     * holding the target method has already loaded and been passed through
     * untouched. Once we learn the enum's real name, retransform any already-loaded
     * class matching the given shape test, so we don't miss that case.
     */
    private void retransformLaterFor(String enumInternalName, ClassLoader loader) {
        Thread worker = new Thread(() -> {
            try {
                ClassLoader lookupLoader = loader != null ? loader : ClassLoader.getSystemClassLoader();
                Class<?> enumClass = Class.forName(enumInternalName.replace('/', '.'), false, lookupLoader);

                for (Class<?> loadedClass : instrumentation.getAllLoadedClasses()) {
                    if (!instrumentation.isModifiableClass(loadedClass)) {
                        continue;
                    }
                    try {
                        boolean matches = false;
                        for (Method method : loadedClass.getDeclaredMethods()) {
                            Class<?>[] params = method.getParameterTypes();
                            boolean legacyShape = params.length == 0 && method.getReturnType() == enumClass;
                            boolean modernShape = params.length == 1 && params[0] == enumClass
                                    && method.getReturnType() == loadedClass;
                            if (legacyShape || modernShape) {
                                matches = true;
                                break;
                            }
                        }
                        if (matches) {
                            System.out.println("[mcrl] retransforming already-loaded " + loadedClass.getName());
                            instrumentation.retransformClasses(loadedClass);
                        }
                    } catch (Throwable ignored) {
                        // Not introspectable right now - if it loads again fresh it's still covered by transform().
                    }
                }
            } catch (Throwable t) {
                System.err.println("[mcrl] could not scan already-loaded classes for the chat gate");
                t.printStackTrace();
            }
        }, "mcrl-retransform");
        worker.setDaemon(true);
        worker.start();
    }
}
