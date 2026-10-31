package dev.invfreeze.client;

import dev.invfreeze.InventoryFreezeMod;
import dev.invfreeze.config.FreezeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.client.resources.IResourceManager;

/**
 * Installs {@link FreezeEntityRenderer} in place of the vanilla EntityRenderer.
 *
 * Why this is needed: in 1.8.9 the only place where "render the world" can be
 * skipped without also skipping the HUD is EntityRenderer#renderWorld, and Forge
 * exposes no cancellable event for it. Replacing the public Minecraft#entityRenderer
 * field with a subclass is the least invasive option (no coremod / bytecode edits).
 *
 * The hook is skipped (never forced) when another mod already replaced the renderer,
 * and by default when OptiFine is present.
 */
public final class RendererHook {

    private static boolean installed = false;
    private static String status = "not attempted";

    private RendererHook() {
    }

    public static boolean isInstalled() {
        return installed;
    }

    public static String getStatus() {
        return status;
    }

    public static void install(Minecraft mc) {
        if (installed) {
            return;
        }
        if (mc.entityRenderer == null) {
            status = "Minecraft#entityRenderer is null (too early?)";
            InventoryFreezeMod.LOGGER.warn("Renderer hook not installed: {}", status);
            return;
        }
        if (OptiFineCompat.isOptiFinePresent() && !FreezeConfig.forceRendererHook) {
            status = "OptiFine detected - hook skipped (set forceRendererHook=true to try anyway)";
            InventoryFreezeMod.LOGGER.warn("Renderer hook not installed: {}", status);
            return;
        }
        if (mc.entityRenderer.getClass() != EntityRenderer.class) {
            status = "another mod already replaced EntityRenderer (" + mc.entityRenderer.getClass().getName() + ")";
            InventoryFreezeMod.LOGGER.warn("Renderer hook not installed: {}", status);
            return;
        }
        try {
            IResourceManager resourceManager = mc.getResourceManager();
            FreezeEntityRenderer renderer = new FreezeEntityRenderer(mc, resourceManager);
            mc.entityRenderer = renderer;
            if (resourceManager instanceof IReloadableResourceManager) {
                // Keep the new instance in the resource-reload chain (shader reloads).
                ((IReloadableResourceManager) resourceManager).registerReloadListener(renderer);
            }
            installed = true;
            status = "installed";
            InventoryFreezeMod.LOGGER.info("Renderer hook installed.");
        } catch (Throwable t) {
            status = "failed: " + t;
            InventoryFreezeMod.LOGGER.error("Renderer hook failed, mod stays inactive.", t);
        }
    }
}
