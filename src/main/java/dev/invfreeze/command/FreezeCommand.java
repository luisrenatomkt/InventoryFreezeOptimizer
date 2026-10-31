package dev.invfreeze.command;

import dev.invfreeze.client.SceneFreezer;
import dev.invfreeze.config.FreezeConfig;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.util.ChatComponentText;

/** Client-side command: /invfreeze status | toggle | reset */
public class FreezeCommand extends CommandBase {

    @Override
    public String getCommandName() {
        return "invfreeze";
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/invfreeze <status|toggle|reset>";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }

    @Override
    public boolean canCommandSenderUseCommand(ICommandSender sender) {
        return true;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) throws CommandException {
        if (args.length != 1) {
            throw new WrongUsageException(getCommandUsage(sender));
        }
        String sub = args[0].toLowerCase();
        if ("status".equals(sub)) {
            reply(sender, "enabled=" + FreezeConfig.enabled
                    + ", freezeScene=" + FreezeConfig.freezeScene
                    + ", useStaticImage=" + FreezeConfig.useStaticImage
                    + ", renderOptimizations=" + FreezeConfig.renderOptimizations
                    + ", fixTrails=" + FreezeConfig.fixTrails);
            reply(sender, SceneFreezer.INSTANCE.describe());
        } else if ("toggle".equals(sub)) {
            FreezeConfig.setEnabled(!FreezeConfig.enabled);
            SceneFreezer.INSTANCE.onConfigChanged();
            reply(sender, "Inventory Freeze " + (FreezeConfig.enabled ? "ENABLED" : "DISABLED"));
        } else if ("reset".equals(sub)) {
            FreezeConfig.resetToDefaults();
            SceneFreezer.INSTANCE.onConfigChanged();
            reply(sender, "Settings restored to defaults.");
        } else {
            throw new WrongUsageException(getCommandUsage(sender));
        }
    }

    private static void reply(ICommandSender sender, String msg) {
        sender.addChatMessage(new ChatComponentText("[InvFreeze] " + msg));
    }
}
