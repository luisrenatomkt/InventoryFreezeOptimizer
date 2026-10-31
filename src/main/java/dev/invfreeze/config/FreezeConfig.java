package dev.invfreeze.config;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;

import java.io.File;

/**
 * Persistent settings (config/inventoryfreeze.cfg). Fields are volatile
 * because they are read from the render path every frame.
 */
public final class FreezeConfig {

    public static final String CATEGORY = Configuration.CATEGORY_GENERAL;

    private static final String[] NAMES = {
            "enabled",
            "freezeScene",
            "useStaticImage",
            "renderOptimizations",
            "fixTrails",
            "applyToAllContainers",
            "forceRendererHook"
    };

    private static final boolean[] DEFAULTS = {
            true,   // enabled
            true,   // freezeScene (default ON as requested)
            true,   // useStaticImage
            true,   // renderOptimizations
            true,   // fixTrails
            true,   // applyToAllContainers
            false   // forceRendererHook
    };

    private static final String[] COMMENTS = {
            "Master switch for the whole mod.",
            "Freeze the scene behind the inventory instead of re-rendering the world every frame.",
            "Keep a single captured image of the scene (uses VRAM = screen width x height x 4 bytes). "
                    + "If false, a solid dark background is drawn instead (no capture, no VRAM).",
            "Extra savings while frozen (skips the per-tick entity ray-trace, which is not needed behind a GUI).",
            "Normalise OpenGL state before container GUIs are drawn to avoid leftover-state artefacts "
                    + "(e.g. ghost trails when dragging items).",
            "Also freeze behind chests and other container GUIs, not only the player inventory / creative screen.",
            "Install the renderer hook even if OptiFine is detected (experimental, untested). Needs a restart."
    };

    public static volatile boolean enabled = DEFAULTS[0];
    public static volatile boolean freezeScene = DEFAULTS[1];
    public static volatile boolean useStaticImage = DEFAULTS[2];
    public static volatile boolean renderOptimizations = DEFAULTS[3];
    public static volatile boolean fixTrails = DEFAULTS[4];
    public static volatile boolean applyToAllContainers = DEFAULTS[5];
    public static volatile boolean forceRendererHook = DEFAULTS[6];

    private static Configuration config;

    private FreezeConfig() {
    }

    public static void init(File file) {
        config = new Configuration(file);
        config.addCustomCategoryComment(CATEGORY, "Inventory Freeze FPS Optimizer settings");
        sync();
    }

    public static Configuration getConfig() {
        return config;
    }

    private static Property prop(int i) {
        return config.get(CATEGORY, NAMES[i], DEFAULTS[i], COMMENTS[i]);
    }

    /** Reads every value from the Configuration object into the static fields and saves if needed. */
    public static void sync() {
        if (config == null) {
            return;
        }
        enabled = prop(0).getBoolean();
        freezeScene = prop(1).getBoolean();
        useStaticImage = prop(2).getBoolean();
        renderOptimizations = prop(3).getBoolean();
        fixTrails = prop(4).getBoolean();
        applyToAllContainers = prop(5).getBoolean();
        forceRendererHook = prop(6).getBoolean();
        if (config.hasChanged()) {
            config.save();
        }
    }

    public static void resetToDefaults() {
        if (config == null) {
            return;
        }
        for (int i = 0; i < NAMES.length; i++) {
            prop(i).set(DEFAULTS[i]);
        }
        sync();
        config.save();
    }

    public static void setEnabled(boolean value) {
        if (config == null) {
            enabled = value;
            return;
        }
        prop(0).set(value);
        sync();
        config.save();
    }
}
