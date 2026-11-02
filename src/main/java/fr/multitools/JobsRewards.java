package fr.multitools;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/** Donne de l'xp ExcellentJobs via commande console, selon le bloc cassé. */
public final class JobsRewards {

    private record Reward(String job, double min, double max, Set<Material> blocks) {}

    private final MultiToolsPlugin plugin;
    private final List<Reward> rewards = new ArrayList<>();
    private boolean enabled;
    private boolean firstMatchOnly;
    private String command = "jobs xp give {player} {job} {xp} -s";

    public JobsRewards(MultiToolsPlugin plugin) { this.plugin = plugin; }

    public void load(ConfigurationSection sec) {
        rewards.clear();
        enabled = false;
        if (sec == null || !sec.getBoolean("enabled", true)) return;
        if (!Bukkit.getPluginManager().isPluginEnabled("ExcellentJobs")) {
            plugin.getLogger().info("ExcellentJobs non détecté : récompenses de métiers désactivées.");
            return;
        }
        command = sec.getString("command", command);
        firstMatchOnly = sec.getBoolean("first-match-only", true);

        for (Map<?, ?> raw : sec.getMapList("rewards")) {
            Object job = raw.get("job");
            Object xp = raw.get("xp");
            Object blocks = raw.get("blocks");
            if (job == null || xp == null || !(blocks instanceof List<?> list)) continue;
            double[] range = parseXp(xp);
            Set<Material> set = EnumSet.noneOf(Material.class);
            for (Object o : list) resolve(String.valueOf(o), set);
            rewards.add(new Reward(String.valueOf(job), range[0], range[1], set));
        }
        enabled = !rewards.isEmpty();
        plugin.getLogger().info("ExcellentJobs : " + rewards.size() + " règles d'xp chargées.");
    }

    private double[] parseXp(Object o) {
        if (o instanceof Number n) return new double[]{n.doubleValue(), n.doubleValue()};
        String s = String.valueOf(o).trim();
        try {
            int idx = s.indexOf('-', 1);
            if (idx > 0) {
                double a = Double.parseDouble(s.substring(0, idx).trim());
                double b = Double.parseDouble(s.substring(idx + 1).trim());
                return new double[]{Math.min(a, b), Math.max(a, b)};
            }
            double v = Double.parseDouble(s);
            return new double[]{v, v};
        } catch (NumberFormatException ex) {
            plugin.getLogger().warning("xp invalide dans config.yml : " + s);
            return new double[]{0, 0};
        }
    }

    private void resolve(String entry, Set<Material> out) {
        String e = entry.trim();
        if (e.equals("*")) {
            for (Material m : Material.values()) {
                if (m.isBlock() && !m.isAir() && !m.name().startsWith("LEGACY_")) out.add(m);
            }
            return;
        }
        if (e.startsWith("#")) {
            NamespacedKey k = NamespacedKey.fromString(e.substring(1).toLowerCase(Locale.ROOT));
            Tag<Material> tag = k == null ? null : Bukkit.getTag(Tag.REGISTRY_BLOCKS, k, Material.class);
            if (tag == null) plugin.getLogger().warning("Tag de blocs inconnu : " + e);
            else out.addAll(tag.getValues());
            return;
        }
        Material m = Material.matchMaterial(e);
        if (m == null) plugin.getLogger().warning("Matériau inconnu : " + e);
        else out.add(m);
    }

    public void reward(Player p, Material type, double multiplier) {
        if (!enabled) return;
        for (Reward r : rewards) {
            if (!r.blocks().contains(type)) continue;
            double xp = r.min() == r.max() ? r.min() : ThreadLocalRandom.current().nextDouble(r.min(), r.max());
            xp *= multiplier;
            if (xp > 0) {
                String cmd = command.replace("{player}", p.getName())
                        .replace("{job}", r.job())
                        .replace("{xp}", fmt(xp));
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
            if (firstMatchOnly) break;
        }
    }

    private String fmt(double v) {
        return BigDecimal.valueOf(Math.round(v * 100.0) / 100.0).stripTrailingZeros().toPlainString();
    }
}
