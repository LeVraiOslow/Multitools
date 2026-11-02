package fr.multitools;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public final class MultiToolsCommand implements TabExecutor {

    private final MultiToolsPlugin plugin;

    public MultiToolsCommand(MultiToolsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        ToolManager tools = plugin.tools();
        if (a.length == 0 || a[0].equalsIgnoreCase("help")) {
            if (!s.hasPermission("multitools.admin")) { s.sendMessage(plugin.msg("no-permission", null)); return true; }
            for (String line : plugin.messages().getStringList("help")) s.sendMessage(plugin.text(line, null));
            return true;
        }
        if (!s.hasPermission("multitools.admin")) { s.sendMessage(plugin.msg("no-permission", null)); return true; }

        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "reload" -> {
                plugin.reloadAll();
                s.sendMessage(plugin.msg("reloaded", null));
            }
            case "give" -> {
                if (a.length < 2) { s.sendMessage(plugin.msg("usage-give", null)); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { s.sendMessage(plugin.msg("player-not-found", Map.of("name", a[1]))); return true; }
                Tier tier = a.length >= 3 ? tools.tier(a[2]) : tools.firstTier();
                if (tier == null) { s.sendMessage(plugin.msg("invalid-tier", Map.of("name", a.length >= 3 ? a[2] : "?"))); return true; }
                ItemStack item = tools.createNew(tier);
                t.getInventory().addItem(item).values()
                        .forEach(left -> t.getWorld().dropItemNaturally(t.getLocation(), left));
                Map<String, String> ph = Map.of("name", t.getName(), "tier", tier.name);
                s.sendMessage(plugin.msg("given-sender", ph));
                if (!s.equals(t)) t.sendMessage(plugin.msg("given-target", ph));
            }
            case "settier" -> {
                if (a.length < 3) { s.sendMessage(plugin.msg("usage-settier", null)); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { s.sendMessage(plugin.msg("player-not-found", Map.of("name", a[1]))); return true; }
                Tier tier = tools.tier(a[2]);
                if (tier == null) { s.sendMessage(plugin.msg("invalid-tier", Map.of("name", a[2]))); return true; }
                ToolManager.State st = tools.read(t.getInventory().getItemInMainHand());
                if (st == null) { s.sendMessage(plugin.msg("not-holding", null)); return true; }
                t.getInventory().setItemInMainHand(tools.create(tier, 0, st.total()));
                s.sendMessage(plugin.msg("settier-done", Map.of("name", t.getName(), "tier", tier.name)));
            }
            case "addblocks" -> {
                if (a.length < 3) { s.sendMessage(plugin.msg("usage-addblocks", null)); return true; }
                Player t = Bukkit.getPlayerExact(a[1]);
                if (t == null) { s.sendMessage(plugin.msg("player-not-found", Map.of("name", a[1]))); return true; }
                long n;
                try { n = Long.parseLong(a[2]); } catch (NumberFormatException ex) { n = 0; }
                if (n <= 0) { s.sendMessage(plugin.msg("invalid-amount", null)); return true; }
                if (tools.process(t, t.getInventory().getItemInMainHand(), n) == null) {
                    s.sendMessage(plugin.msg("not-holding", null)); return true;
                }
                s.sendMessage(plugin.msg("addblocks-done", Map.of("name", t.getName(), "amount", String.valueOf(n))));
            }
            default -> {
                for (String line : plugin.messages().getStringList("help")) s.sendMessage(plugin.text(line, null));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        if (!s.hasPermission("multitools.admin")) return List.of();
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(List.of("give", "settier", "addblocks", "reload", "help"));
        } else if (a.length == 2 && !a[0].equalsIgnoreCase("reload")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        } else if (a.length == 3 && (a[0].equalsIgnoreCase("give") || a[0].equalsIgnoreCase("settier"))) {
            plugin.tools().tiers().forEach(t -> out.add(t.id));
        }
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
