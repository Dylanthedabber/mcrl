package mcrl.agent;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Patches authlib's ProfileResult.actions() so a Mojang-flagged forced-name-change or banned-skin
// action no longer makes the client refuse to connect to third-party servers; Mojang's own
// servers/Realms enforce their moderation independently of this and are unaffected. Unobfuscated,
// so matches by real name, same as AccountFlagsTransformer.
public class ProfileActionsTransformer implements ClassFileTransformer {

    private static final String PROFILE_RESULT = "com/mojang/authlib/yggdrasil/ProfileResult";
    private static final String PROFILE_ACTION_TYPE = "com/mojang/authlib/yggdrasil/ProfileActionType";
    private static final String TARGET_DESCRIPTOR = "()Ljava/util/Set;";
    // authlib versions before MC 1.20.4 don't have ProfileActionType at all; this gracefully
    // no-ops there, same as AccountFlagsTransformer already does for flags older versions lack.
    private static final List<String> STRIP_ACTIONS = Arrays.asList("FORCED_NAME_CHANGE", "USING_BANNED_SKIN");

    private final ConcurrentHashMap<ClassLoader, List<String>> presentActionsCache = new ConcurrentHashMap<>();
    private final boolean verbose;

    public ProfileActionsTransformer(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                             ProtectionDomain protectionDomain, byte[] classfileBuffer) {
        if (className == null || classfileBuffer == null) {
            return null;
        }
        if (loader == null || className.startsWith("java/") || className.startsWith("javax/")
                || className.startsWith("jdk/") || className.startsWith("sun/") || className.startsWith("mcrl/")) {
            return null;
        }
        if (!PROFILE_RESULT.equals(className)) {
            return null;
        }
        try {
            List<String> presentActions = detectPresentActionsToStrip(loader);
            if (presentActions.isEmpty()) {
                return null;
            }
            return patchActionsGetter(new ClassReader(classfileBuffer), className, loader, presentActions);
        } catch (Throwable t) {
            if (verbose) {
                System.err.println("[mcrl] failed to inspect " + className);
                t.printStackTrace();
            }
            return null;
        }
    }

    // Reads real ProfileActionType bytes (never a real class load) to see which of the actions we
    // want to strip actually exist in this authlib version.
    private List<String> detectPresentActionsToStrip(ClassLoader loader) {
        return presentActionsCache.computeIfAbsent(loader, l -> {
            Set<String> presentFields = new LinkedHashSet<>();
            try (InputStream in = l.getResourceAsStream(PROFILE_ACTION_TYPE + ".class")) {
                if (in == null) {
                    return Collections.emptyList();
                }
                new ClassReader(readAllBytes(in)).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public FieldVisitor visitField(int access, String name, String descriptor,
                                                    String signature, Object value) {
                        if ((access & Opcodes.ACC_ENUM) != 0) {
                            presentFields.add(name);
                        }
                        return null;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            } catch (Throwable t) {
                return Collections.emptyList();
            }
            List<String> toStrip = new ArrayList<>();
            for (String action : STRIP_ACTIONS) {
                if (presentFields.contains(action)) {
                    toStrip.add(action);
                }
            }
            return toStrip;
        });
    }

    // className is already confirmed to be PROFILE_RESULT, so find its actions() record accessor
    // and rewrite it to return a copy with the confirmed-present actions removed.
    private byte[] patchActionsGetter(ClassReader reader, String className, ClassLoader loader,
                                       List<String> toStrip) {
        ClassWriter writer = newClassWriter(reader, loader);
        boolean[] patched = {false};

        ClassVisitor classVisitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                              String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!"actions".equals(name) || !TARGET_DESCRIPTOR.equals(descriptor)
                        || (access & (Opcodes.ACC_STATIC | Opcodes.ACC_ABSTRACT)) != 0) {
                    return mv;
                }
                patched[0] = true;
                if (verbose) {
                    System.out.println("[mcrl] patching (profile actions) " + className + "#" + name + descriptor
                            + " strip=" + toStrip);
                }
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitInsn(int opcode) {
                        if (opcode == Opcodes.ARETURN) {
                            // actions() is zero-arg, so only slot 0 (this) is in use; 1 and 2 are free.
                            int origSlot = 1;
                            int newSlot = 2;

                            super.visitVarInsn(Opcodes.ASTORE, origSlot);

                            super.visitTypeInsn(Opcodes.NEW, "java/util/HashSet");
                            super.visitInsn(Opcodes.DUP);
                            super.visitVarInsn(Opcodes.ALOAD, origSlot);
                            super.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/util/HashSet", "<init>",
                                    "(Ljava/util/Collection;)V", false);
                            super.visitVarInsn(Opcodes.ASTORE, newSlot);

                            for (String actionName : toStrip) {
                                super.visitVarInsn(Opcodes.ALOAD, newSlot);
                                super.visitFieldInsn(Opcodes.GETSTATIC, PROFILE_ACTION_TYPE, actionName,
                                        "L" + PROFILE_ACTION_TYPE + ";");
                                super.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Set", "remove",
                                        "(Ljava/lang/Object;)Z", true);
                                super.visitInsn(Opcodes.POP);
                            }

                            super.visitVarInsn(Opcodes.ALOAD, newSlot);
                        }
                        super.visitInsn(opcode);
                    }
                };
            }
        };

        reader.accept(classVisitor, 0);
        return patched[0] ? writer.toByteArray() : null;
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private ClassWriter newClassWriter(ClassReader reader, ClassLoader loader) {
        return new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected ClassLoader getClassLoader() {
                return loader != null ? loader : super.getClassLoader();
            }
        };
    }
}
