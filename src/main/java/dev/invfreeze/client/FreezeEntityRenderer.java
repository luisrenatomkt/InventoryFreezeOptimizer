package dev.invfreeze.client;

import dev.invfreeze.config.FreezeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.resources.IResourceManager;

/**
 * EntityRenderer subclass that replaces the (expensive) world pass with the
 * frozen background while a container GUI is open.
 *
 * Call order in vanilla's updateCameraAndRender is: renderWorld -> HUD overlay
 * -> GUI screen. Only renderWorld is replaced, so the HUD (hearts, hunger,
 * hotbar) and the GUI keep rendering live every frame.
 */
public class FreezeEntityRenderer extends EntityRenderer {

    private final Minecraft mcRef;

    public FreezeEntityRenderer(Minecraft mc, IResourceManager resourceManager) {
        super(mc, resourceManager);
        this.mcRef = mc;
    }

    @Override
    public void renderWorld(float partialTicks, long finishTimeNano) {
        SceneFreezer freezer = SceneFreezer.INSTANCE;

        if (freezer.isFreezeActive(mcRef)) {
            if (freezer.drawFrozenBackground(mcRef)) {
                return; // world pass skipped; background fully overwritten this frame
            }
            // No valid snapshot yet (first frame, resize, world change): render normally once, then capture.
            super.renderWorld(partialTicks, finishTimeNano);
            freezer.captureAfterWorldRender(mcRef);
            return;
        }

        freezer.releaseIfHeld();
        super.renderWorld(partialTicks, finishTimeNano);
    }

    @Override
    public void getMouseOver(float partialTicks) {
        // Vanilla ray-traces for the looked-at block/entity every tick, even with a GUI open.
        // Behind a frozen scene the result can't be used, so skip it (opt-out via config).
        if (FreezeConfig.renderOptimizations) {
            SceneFreezer freezer = SceneFreezer.INSTANCE;
            if (freezer.isFreezeActive(mcRef) && freezer.hasFrozenFrame(mcRef)) {
                return;
            }
        }
        super.getMouseOver(partialTicks);
    }
}
