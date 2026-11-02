package fr.multitools;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

public final class ToolListener implements Listener {

    private final MultiToolsPlugin plugin;

    public ToolListener(MultiToolsPlugin plugin) { this.plugin = plugin; }

    // ignoreCancelled = false : certains modes (OneBlock) annulent l'event puis gèrent le bloc eux-mêmes
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!plugin.tools().isTool(hand)) return;

        boolean debug = plugin.getConfig().getBoolean("settings.debug", false);
        if (debug) {
            plugin.getLogger().info("[debug] " + p.getName() + " casse " + e.getBlock().getType()
                    + " monde=" + p.getWorld().getName() + " annulé=" + e.isCancelled());
        }

        if (!p.hasPermission("multitools.use")) return;
        if (p.getGameMode() == GameMode.CREATIVE && !plugin.getConfig().getBoolean("settings.count-creative", false)) return;
        if (plugin.isDisabledWorld(p.getWorld().getName())) return;
        if (e.isCancelled() && !allowCancelled(p, e.getBlock())) return;

        Material type = e.getBlock().getType(); // encore le bloc d'origine à ce stade
        if (plugin.isIgnored(type)) return;

        Tier tier = plugin.tools().process(p, hand, 1);
        if (tier == null) return;

        plugin.jobs().reward(p, type, tier.jobsMultiplier);
    }

    /** Event annulé : on compte quand même dans certains mondes (OneBlock SuperiorSkyblock2). */
    private boolean allowCancelled(Player p, Block b) {
        String base = "settings.count-cancelled-events.";
        if (!plugin.getConfig().getBoolean(base + "enabled", true)) return false;
        var worlds = plugin.getConfig().getStringList(base + "worlds");
        if (!worlds.isEmpty() && !worlds.contains(b.getWorld().getName())) return false;
        if (plugin.getConfig().getBoolean(base + "require-island-member", true)) {
            return SkyblockHook.isIslandMember(plugin, p, b.getLocation());
        }
        return true;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (plugin.getConfig().getBoolean("settings.refresh-on-join", true)) {
            plugin.tools().refreshInventory(e.getPlayer());
        }
    }
}
