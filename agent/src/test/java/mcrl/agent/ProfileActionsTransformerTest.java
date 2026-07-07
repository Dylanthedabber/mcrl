package mcrl.agent;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.yggdrasil.ProfileActionType;
import com.mojang.authlib.yggdrasil.ProfileResult;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import static mcrl.agent.TestSupport.classBytes;
import static mcrl.agent.TestSupport.internalName;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProfileActionsTransformerTest {

    private final ClassLoader testLoader = getClass().getClassLoader();

    @SuppressWarnings("unchecked")
    @Test
    void stripsForcedNameChangeAndBannedSkin() throws Exception {
        ProfileActionsTransformer transformer = new ProfileActionsTransformer(false);

        byte[] patched = transformer.transform(testLoader, internalName(ProfileResult.class),
                null, null, classBytes(ProfileResult.class));
        assertNotNull(patched, "expected actions() to be patched");

        TestSupport.ByteClassLoader loader = new TestSupport.ByteClassLoader(testLoader);
        loader.override(internalName(ProfileResult.class), patched);
        Class<?> patchedClass = Class.forName(ProfileResult.class.getName(), true, loader);

        Constructor<?> ctor = patchedClass.getDeclaredConstructor(GameProfile.class, Set.class);
        GameProfile profile = new GameProfile(UUID.randomUUID(), "Steve");
        Set<ProfileActionType> starting =
                EnumSet.of(ProfileActionType.FORCED_NAME_CHANGE, ProfileActionType.USING_BANNED_SKIN);
        Object instance = ctor.newInstance(profile, starting);

        Method actionsMethod = patchedClass.getMethod("actions");
        Set<ProfileActionType> result = (Set<ProfileActionType>) actionsMethod.invoke(instance);

        assertTrue(result.isEmpty(), "both flagged actions should have been stripped");
    }

    @SuppressWarnings("unchecked")
    @Test
    void emptyStartingSetStaysEmptyWithoutCrashing() throws Exception {
        ProfileActionsTransformer transformer = new ProfileActionsTransformer(false);
        byte[] patched = transformer.transform(testLoader, internalName(ProfileResult.class),
                null, null, classBytes(ProfileResult.class));

        TestSupport.ByteClassLoader loader = new TestSupport.ByteClassLoader(testLoader);
        loader.override(internalName(ProfileResult.class), patched);
        Class<?> patchedClass = Class.forName(ProfileResult.class.getName(), true, loader);

        Object instance = patchedClass.getDeclaredConstructor(GameProfile.class)
                .newInstance(new GameProfile(UUID.randomUUID(), "Steve"));
        Set<ProfileActionType> result = (Set<ProfileActionType>) patchedClass.getMethod("actions").invoke(instance);

        assertEquals(0, result.size());
    }

    @Test
    void nonProfileResultClassIsNeverTouched() throws Exception {
        ProfileActionsTransformer transformer = new ProfileActionsTransformer(false);
        byte[] patched = transformer.transform(testLoader, internalName(GameProfile.class),
                null, null, classBytes(GameProfile.class));
        assertNull(patched);
    }
}
