package com.cinematicchests.client;

import com.cinematicchests.CinematicChests;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

/**
 * Keeps another mod's support from taking the game down. The rack calls into Sophisticated Core by name; a later version that
 * renames or changes one of those methods would fail with a linkage error the first time it is called. Here every call goes
 * through a guard: on such an error the support switches itself off for the rest of the session (one warning in the log), the
 * call answers "nothing" (no upgrades, nothing open), and storage keeps working as it does without the mod.
 */
final class GuardedRack implements InvocationHandler {
    private static volatile boolean broken;

    private final UpgradeRack inner;

    private GuardedRack(UpgradeRack inner) {
        this.inner = inner;
    }

    static boolean broken() {
        return broken;
    }

    static UpgradeRack wrap(UpgradeRack inner) {
        return (UpgradeRack) Proxy.newProxyInstance(UpgradeRack.class.getClassLoader(), new Class<?>[]{UpgradeRack.class}, new GuardedRack(inner));
    }

    /** Records a failure of the support (from anywhere it is called), once. */
    static void fail(Throwable error) {
        if (!broken) {
            broken = true;
            CinematicChests.LOGGER.warn("[Cinematic Chests] the installed Sophisticated version does not match what this add-on was built for; "
                    + "its upgrades stay in the window this session (storage scenes keep working)", error);
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(inner, args);
        }
        if (!broken) {
            try {
                return method.invoke(inner, args);
            } catch (InvocationTargetException e) {
                if (!(e.getCause() instanceof LinkageError)) throw e.getCause();
                fail(e.getCause());
            }
        }
        return nothing(method);
    }

    /** What a call answers once the support is off: no upgrades, nothing open, nothing to show. */
    private static Object nothing(Method method) {
        Class<?> type = method.getReturnType();
        if (type == boolean.class) return false;
        if (type == int.class) {
            // positions answer "none"; counts and keys answer zero
            return switch (method.getName()) {
                case "open", "activeSlot", "chosen", "menuSlot" -> -1;
                default -> 0;
            };
        }
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == Optional.class) return Optional.empty();
        if (type == List.class) return List.of();
        return null;
    }
}
