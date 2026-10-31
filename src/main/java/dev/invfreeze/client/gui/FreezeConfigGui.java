package dev.invfreeze.client.gui;

import dev.invfreeze.InventoryFreezeMod;
import dev.invfreeze.config.FreezeConfig;
import net.minecraft.client.gui.GuiScreen;
import net.minecraftforge.common.config.ConfigElement;
import net.minecraftforge.fml.client.config.GuiConfig;
import net.minecraftforge.fml.client.config.IConfigElement;

import java.util.List;

/**
 * Settings screen (Mods -> Inventory Freeze -> Config). Forge's GuiConfig already
 * provides the "Default" button, which restores every entry to its default value.
 */
public class FreezeConfigGui extends GuiConfig {

    public FreezeConfigGui(GuiScreen parent) {
        super(parent, elements(), InventoryFreezeMod.MODID, false, false, InventoryFreezeMod.NAME);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static List<IConfigElement> elements() {
        return new ConfigElement(FreezeConfig.getConfig().getCategory(FreezeConfig.CATEGORY)).getChildElements();
    }
}
