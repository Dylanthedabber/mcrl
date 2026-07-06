package mcrl.agent;

/**
 * Pure reflection, no Minecraft/Forge/Fabric classes on the compile classpath: matches
 * enum constants by their .name() rather than a hardcoded class reference, since the
 * enum's own class name varies by loader, mapping scheme, and Minecraft era.
 */
public final class ChatRestrictionFix {

    private ChatRestrictionFix() {
    }

    /** Legacy shape (1.19-1.21.11): swap a DISABLED_BY_PROFILE return value for ENABLED. */
    public static Object fixReturnValue(Object status) {
        if (!(status instanceof Enum)) {
            return status;
        }
        Enum<?> current = (Enum<?>) status;
        if (!"DISABLED_BY_PROFILE".equals(current.name())) {
            return status;
        }
        for (Object constant : status.getClass().getEnumConstants()) {
            if (constant instanceof Enum && "ENABLED".equals(((Enum<?>) constant).name())) {
                return constant;
            }
        }
        return status;
    }

    /** Modern shape (26.1+): identifies a restriction argument that should be dropped. */
    public static boolean isProfileRestriction(Object restriction) {
        return restriction instanceof Enum && "DISABLED_BY_PROFILE".equals(((Enum<?>) restriction).name());
    }
}
