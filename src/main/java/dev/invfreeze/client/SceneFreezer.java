package dev.invfreeze.client;

import dev.invfreeze.InventoryFreezeMod;
import dev.invfreeze.config.FreezeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiContainerCreative;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.world.World;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

/**
 * Owns the static snapshot of the 3D scene and draws it.
 *
 * Capture: right after a normal renderWorld() and BEFORE the HUD is drawn, the
 * currently bound framebuffer is copied into a texture (glCopyTexSubImage2D).
 * The HUD is therefore never part of the snapshot, so live hearts/hunger never
 * double up with a stale copy.
 *
 * Draw: every frozen frame a full-screen opaque quad is drawn. Every pixel of
 * the framebuffer is overwritten, so nothing from the previous frame can
 * survive underneath the GUI (this is what removes drag trails caused by a
 * framebuffer that is no longer cleared by the skipped world pass).
 *
 * All GL calls here must run on the main (render) thread.
 */
public final class SceneFreezer {

    public static final SceneFreezer INSTANCE = new SceneFreezer();

    private int texture = 0;
    private int texW = 0;
    private int texH = 0;
    private boolean valid = false;
    private boolean failed = false;
    private WeakReference<World> capturedWorld = new WeakReference<World>(null);

    private long framesFromSnapshot = 0L;
    private long framesCaptured = 0L;

    private SceneFreezer() {
    }

    // ------------------------------------------------------------------ state

    /** True when the current screen is one we should freeze behind. */
    public boolean isFreezeActive(Minecraft mc) {
        if (!FreezeConfig.enabled || !FreezeConfig.freezeScene) {
            return false;
        }
        if (mc.theWorld == null || mc.thePlayer == null) {
            return false;
        }
        GuiScreen screen = mc.currentScreen;
        if (screen == null) {
            return false;
        }
        if (screen instanceof GuiInventory || screen instanceof GuiContainerCreative) {
            return true;
        }
        return FreezeConfig.applyToAllContainers && screen instanceof GuiContainer;
    }

    /** True when something valid can be drawn this frame without rendering the world. */
    public boolean hasFrozenFrame(Minecraft mc) {
        if (!FreezeConfig.useStaticImage || failed) {
            return true; // solid-colour mode needs no snapshot
        }
        return isSnapshotCurrent(mc);
    }

    private boolean isSnapshotCurrent(Minecraft mc) {
        return valid
                && texture != 0
                && texW == fbWidth(mc)
                && texH == fbHeight(mc)
                && capturedWorld.get() == mc.theWorld;
    }

    public boolean holdsResources() {
        return texture != 0;
    }

    /** Marks the snapshot stale (called on every GUI change). No GL work. */
    public void invalidate() {
        valid = false;
    }

    public void onConfigChanged() {
        failed = false;
        invalidate();
    }

    // ------------------------------------------------------------------ draw

    /**
     * Draws the frozen background. Returns false if no valid snapshot exists
     * yet (caller must then render the world normally and call capture).
     */
    public boolean drawFrozenBackground(Minecraft mc) {
        if (!FreezeConfig.useStaticImage || failed) {
            fillSolid();
            framesFromSnapshot++;
            return true;
        }
        if (!isSnapshotCurrent(mc)) {
            return false;
        }
        drawSnapshotQuad();
        framesFromSnapshot++;
        return true;
    }

    /**
     * Alternative mode (no capture, no VRAM): clear to a dark colour. This runs
     * BEFORE the HUD and the GUI are drawn in the frame, so no UI element is erased.
     */
    private void fillSolid() {
        GlStateManager.depthMask(true);
        GlStateManager.clearColor(0.05F, 0.05F, 0.06F, 1.0F);
        GlStateManager.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        // The clear colour is re-set by vanilla before every colour clear, so it is not restored here.
    }

    private void drawSnapshotQuad() {
        GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.pushMatrix();
        GlStateManager.loadIdentity();

        GlStateManager.disableDepth();
        GlStateManager.depthMask(false);
        GlStateManager.disableBlend();
        GlStateManager.disableAlpha();
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        GlStateManager.disableCull();
        GlStateManager.enableTexture2D();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.bindTexture(texture);

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer wr = tessellator.getWorldRenderer();
        wr.begin(7, DefaultVertexFormats.POSITION_TEX);
        wr.pos(-1.0D, -1.0D, 0.0D).tex(0.0D, 0.0D).endVertex();
        wr.pos(1.0D, -1.0D, 0.0D).tex(1.0D, 0.0D).endVertex();
        wr.pos(1.0D, 1.0D, 0.0D).tex(1.0D, 1.0D).endVertex();
        wr.pos(-1.0D, 1.0D, 0.0D).tex(0.0D, 1.0D).endVertex();
        tessellator.draw();

        // Fresh depth buffer for the HUD / GUI that follow.
        GlStateManager.depthMask(true);
        GlStateManager.clear(GL11.GL_DEPTH_BUFFER_BIT);

        GlStateManager.enableDepth();
        GlStateManager.enableAlpha();
        GlStateManager.enableCull();

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.popMatrix();
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.popMatrix();
    }

    // ------------------------------------------------------------------ capture

    /** Copies the just-rendered world (no HUD yet) from the bound framebuffer into the snapshot texture. */
    public void captureAfterWorldRender(Minecraft mc) {
        if (!FreezeConfig.useStaticImage || failed) {
            return;
        }
        int w = fbWidth(mc);
        int h = fbHeight(mc);
        if (w <= 0 || h <= 0) {
            return; // minimised window
        }
        try {
            int max = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
            if (w > max || h > max) {
                fail("screen " + w + "x" + h + " exceeds GL_MAX_TEXTURE_SIZE " + max);
                return;
            }
            GL11.glGetError(); // flush any error raised by earlier code so it isn't blamed on us

            if (texture == 0 || texW != w || texH != h) {
                allocate(w, h);
                if (GL11.glGetError() != GL11.GL_NO_ERROR) {
                    fail("texture allocation failed (out of VRAM?)");
                    return;
                }
            }

            GlStateManager.setActiveTexture(OpenGlHelper.defaultTexUnit);
            GlStateManager.bindTexture(texture);
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, 0, 0, w, h);
            if (GL11.glGetError() != GL11.GL_NO_ERROR) {
                fail("glCopyTexSubImage2D failed");
                return;
            }

            capturedWorld = new WeakReference<World>(mc.theWorld);
            valid = true;
            framesCaptured++;
        } catch (Throwable t) {
            fail("exception during capture: " + t);
        }
    }

    private void allocate(int w, int h) {
        releaseIfHeld();
        int id = GlStateManager.generateTexture();
        GlStateManager.bindTexture(id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0,
                GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        texture = id;
        texW = w;
        texH = h;
        valid = false;
    }

    private void fail(String why) {
        if (!failed) {
            InventoryFreezeMod.LOGGER.warn("Static snapshot unavailable, using solid background instead: {}", why);
        }
        failed = true;
        releaseIfHeld();
    }

    /** Frees the VRAM held by the snapshot. Main thread only. */
    public void releaseIfHeld() {
        if (texture != 0) {
            GlStateManager.deleteTexture(texture);
            texture = 0;
        }
        texW = 0;
        texH = 0;
        valid = false;
        capturedWorld = new WeakReference<World>(null);
    }

    // ------------------------------------------------------------------ helpers

    private static int fbWidth(Minecraft mc) {
        Framebuffer fb = mc.getFramebuffer();
        if (OpenGlHelper.isFramebufferEnabled() && fb != null) {
            return fb.framebufferWidth;
        }
        return mc.displayWidth;
    }

    private static int fbHeight(Minecraft mc) {
        Framebuffer fb = mc.getFramebuffer();
        if (OpenGlHelper.isFramebufferEnabled() && fb != null) {
            return fb.framebufferHeight;
        }
        return mc.displayHeight;
    }

    public String describe() {
        long vramKb = (texture != 0) ? ((long) texW * texH * 4L) / 1024L : 0L;
        return "hook=" + RendererHook.getStatus()
                + ", snapshot=" + (valid ? "valid" : "none")
                + (failed ? " (fallback: solid colour)" : "")
                + ", vram=" + vramKb + " KiB"
                + ", frozenFramesDrawn=" + framesFromSnapshot
                + ", captures=" + framesCaptured;
    }
}
