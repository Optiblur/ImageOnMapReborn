package pl.iomreborn.command.sub;

import org.bukkit.command.CommandSender;
import pl.iomreborn.ImageOnMapReborn;
import pl.iomreborn.Perm;
import pl.iomreborn.command.SubCommand;

/** /maptool reload: reloads config.yml and the translation files. */
public final class ReloadCommand extends SubCommand {

    public ReloadCommand(ImageOnMapReborn plugin) {
        super(plugin, "reload");
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.ADMINISTRATIVE.has(sender);
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        plugin.reloadSettings();
        messages().send(sender, "command.reload.done");
    }
}
