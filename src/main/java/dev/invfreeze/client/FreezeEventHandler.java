package dev.invfreeze.client;

import dev.invfreeze.InventoryFreezeMod;
import dev.invfreeze.config.FreezeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.GuiScreenEvent;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Event glue. Deliberately small: all rendering decisions are made per frame in
 * {@link FreezeEntityRenderer}, so there is no open/close state to get out of sync.
 */
public class FreezeEventHandler {

    /** Any screen change makes the old snapshot stale (e.g. inventory -> chest, close + reopen). */
    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        SceneFreezer.INSTANCE.invalidate();
    }

    /**
     * Frees VRAM when the frozen state ends (inventory closed, disconnect, world unload).
     * Client ticks run on the main thread, so GL calls are safe here - unlike
     * disconnect / world-unload events, which can arrive on other threads.
     */
    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        SceneFreezer freezer = SceneFreezer.INSTANCE;
        if (freezer.holdsResources() && !freezer.isFreezeActive(Minecraft.getMinecraft())) {
            freezer.releaseIfHeld();
        }
    }

    /**
     * Trail fix, part 2: put the GL state the container GUIs assume into a known
     * state right before they draw. Nothing is cleared; only flags are set.
     */
    @SubscribeEvent
    public void onDrawScreenPre(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (!FreezeConfig.enabled || !FreezeConfig.fixTrails) {
            return;
        }
        if (!(event.gui instanceof GuiContainer)) {
            return;
        }
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableAlpha();
        GlStateManager.alphaFunc(516, 0.1F);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.depthMask(true);
    }

    @SubscribeEvent
    public void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (InventoryFreezeMod.MODID.equals(event.modID)) {
            FreezeConfig.sync();
            SceneFreezer.INSTANCE.onConfigChanged();
        }
    }
}
