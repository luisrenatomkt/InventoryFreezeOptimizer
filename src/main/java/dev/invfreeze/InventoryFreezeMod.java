package dev.invfreeze;

import dev.invfreeze.client.FreezeEventHandler;
import dev.invfreeze.client.RendererHook;
import dev.invfreeze.command.FreezeCommand;
import dev.invfreeze.config.FreezeConfig;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Inventory Freeze FPS Optimizer (client-only).
 *
 * While an inventory/container screen is open, the 3D scene behind the GUI is
 * replaced by a single static snapshot (or a solid colour) so the world is not
 * re-rendered every frame. The HUD (hearts, hunger, hotbar) and the GUI are
 * still drawn live, and nothing about ticking or networking is touched.
 */
@Mod(
        modid = InventoryFreezeMod.MODID,
        name = InventoryFreezeMod.NAME,
        version = InventoryFreezeMod.VERSION,
        acceptedMinecraftVersions = "[1.8.9]",
        clientSideOnly = true,
        acceptableRemoteVersions = "*",
        guiFactory = "dev.invfreeze.client.gui.ConfigGuiFactory"
)
public class InventoryFreezeMod {

    public static final String MODID = "inventoryfreeze";
    public static final String NAME = "Inventory Freeze FPS Optimizer";
    public static final String VERSION = "1.0.0";

    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        FreezeConfig.init(event.getSuggestedConfigurationFile());
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        FreezeEventHandler handler = new FreezeEventHandler();
        // GuiOpenEvent / DrawScreenEvent live on the Forge bus,
        // TickEvent / ConfigChangedEvent live on the FML bus in 1.8.9.
        MinecraftForge.EVENT_BUS.register(handler);
        FMLCommonHandler.instance().bus().register(handler);

        ClientCommandHandler.instance.registerCommand(new FreezeCommand());
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        RendererHook.install(Minecraft.getMinecraft());
    }
}
