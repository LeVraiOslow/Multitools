package fr.multitools;

import org.bukkit.GameMode;
import org.bukkit.Material;
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType().isAir() || !hand.hasItemMeta()) return;
        if (!p.hasPermission("multitools.use")) return;
        if (p.getGameMode() == GameMode.CREATIVE && !plugin.getConfig().getBoolean("settings.count-creative", false)) return;
        if (plugin.isDisabledWorld(p.getWorld().getName())) return;

        Material type = e.getBlock().getType(); // encore le bloc d'origine à ce stade
        if (plugin.isIgnored(type)) return;

        Tier tier = plugin.tools().process(p, hand, 1);
        if (tier == null) return;

        plugin.jobs().reward(p, type, tier.jobsMultiplier);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (plugin.getConfig().getBoolean("settings.refresh-on-join", true)) {
            plugin.tools().refreshInventory(e.getPlayer());
        }
    }
}
