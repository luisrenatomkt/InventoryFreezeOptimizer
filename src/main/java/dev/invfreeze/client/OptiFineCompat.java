package dev.invfreeze.client;

/** Best-effort OptiFine detection (class names are not verified against every OptiFine build). */
public final class OptiFineCompat {

    private static final String[] MARKERS = {
            "optifine.OptiFineForgeTweaker",
            "optifine.OptiFineTweaker",
            "optifine.Patcher"
    };

    private OptiFineCompat() {
    }

    public static boolean isOptiFinePresent() {
        ClassLoader loader = OptiFineCompat.class.getClassLoader();
        for (String name : MARKERS) {
            try {
                Class.forName(name, false, loader);
                return true;
            } catch (Throwable ignored) {
                // not present
            }
        }
        return false;
    }
}
