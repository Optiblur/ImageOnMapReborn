package pl.iomreborn.command.sub;

import org.bukkit.command.CommandSender;
import pl.iomreborn.ImageOnMapReborn;
import pl.iomreborn.Perm;
import pl.iomreborn.command.SubCommand;
import pl.iomreborn.storage.LegacyImporter;

import java.io.File;

/** /maptool import [folder] (alias: migrate): imports maps of the original ImageOnMap again, skipping known ones. */
public final class ImportCommand extends SubCommand {

    public ImportCommand(ImageOnMapReborn plugin) {
        super(plugin, "import", "migrate");
    }

    @Override
    public boolean canUse(CommandSender sender) {
        return Perm.ADMINISTRATIVE.has(sender);
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) {
        LegacyImporter importer = plugin.legacyImporter();
        File source = importer.sourceFolder();
        if (args.length > 0) {
            String folder = args[0];
            if (folder.contains("..") || folder.contains("/") || folder.contains("\\")) {
                usage(sender);
                return;
            }
            source = new File(plugin.getDataFolder().getParentFile(), folder);
        }
        messages().send(sender, "command.import.start", source.getPath());
        LegacyImporter.Result result = importer.run(source, false);
        if (!result.sourceFound()) {
            messages().send(sender, "command.import.not-found", source.getPath());
            return;
        }
        plugin.rendererManager().rescan(true);
        messages().send(sender, "command.import.result", result.storesRead(), result.imagesImported(),
                result.mapsImported(), result.imagesSkipped(), result.filesCopied(), result.errors());
    }
}
