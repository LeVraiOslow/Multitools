package fr.multitools;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Tool;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import io.papermc.paper.registry.set.RegistryKeySet;
import io.papermc.paper.registry.set.RegistrySet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.util.Ticks;
import net.kyori.adventure.util.TriState;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.BlockType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.*;

public final class ToolManager {

    public record State(Tier tier, long progress, long total) {}

    private final MultiToolsPlugin plugin;
    private final NamespacedKey kTool, kTier, kProgress, kTotal;
    private final Map<String, Tier> tiers = new LinkedHashMap<>();
    private RegistryKeySet<BlockType> mineable;
    private DecimalFormat df = new DecimalFormat("#,##0");
    private boolean nexoWarned;
    private final Map<Float, Tool> toolCache = new HashMap<>();

    public ToolManager(MultiToolsPlugin plugin) {
        this.plugin = plugin;
        this.kTool = new NamespacedKey(plugin, "multitool");
        this.kTier = new NamespacedKey(plugin, "tier");
        this.kProgress = new NamespacedKey(plugin, "progress");
        this.kTotal = new NamespacedKey(plugin, "total");
    }

    // ------------------------------------------------------------------ chargement

    public void load(YamlConfiguration cfg) {
        tiers.clear();
        toolCache.clear();

        String sep = plugin.getConfig().getString("settings.number-separator", " ");
        DecimalFormatSymbols sy = new DecimalFormatSymbols(Locale.ROOT);
        sy.setGroupingSeparator(sep.isEmpty() ? ' ' : sep.charAt(0));
        df = new DecimalFormat("#,##0", sy);

        ConfigurationSection root = cfg.getConfigurationSection("tiers");
        if (root == null) {
            plugin.getLogger().severe("tiers.yml : section 'tiers' introuvable !");
            return;
        }
        int i = 0;
        Tier prev = null;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            Tier t = new Tier(id, i++);
            t.name = s.getString("name", id);
            t.itemName = s.getString("item-name", "MultiTools {tier}");
            t.lore = s.getStringList("lore");
            Material mat = Material.matchMaterial(s.getString("material", "NETHERITE_PICKAXE"));
            t.material = mat != null ? mat : Material.NETHERITE_PICKAXE;
            t.itemModel = s.getString("item-model", "");
            t.nexoItem = s.getString("nexo-item", "");
            t.blocksToNext = Math.max(1, s.getLong("blocks-to-next", 3000));
            t.miningSpeed = (float) s.getDouble("mining-speed", 9.0);
            t.jobsMultiplier = s.getDouble("jobs-xp-multiplier", 1.0);

            ConfigurationSection ench = s.getConfigurationSection("enchantments");
            if (ench != null) {
                for (String key : ench.getKeys(false)) {
                    Enchantment e = enchant(key);
                    if (e == null) {
                        plugin.getLogger().warning("Palier " + id + " : enchantement inconnu '" + key + "'");
                        continue;
                    }
                    t.enchants.put(e, ench.getInt(key));
                }
            }
            t.sound = s.getString("level-up.sound", "");
            t.soundVolume = (float) s.getDouble("level-up.volume", 1.0);
            t.soundPitch = (float) s.getDouble("level-up.pitch", 1.0);
            t.commands = s.getStringList("level-up.commands");

            if (prev != null) prev.next = t;
            prev = t;
            tiers.put(id.toLowerCase(Locale.ROOT), t);
        }
        plugin.getLogger().info(tiers.size() + " paliers chargés.");
    }

    private Enchantment enchant(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        NamespacedKey k = n.contains(":") ? NamespacedKey.fromString(n) : NamespacedKey.minecraft(n);
        if (k == null) return null;
        return RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(k);
    }

    public Tier tier(String id) { return id == null ? null : tiers.get(id.toLowerCase(Locale.ROOT)); }
    public Tier firstTier() { return tiers.isEmpty() ? null : tiers.values().iterator().next(); }
    public Collection<Tier> tiers() { return tiers.values(); }

    // ------------------------------------------------------------------ item

    public ItemStack createNew(Tier t) { return create(t, 0, 0); }

    public ItemStack create(Tier t, long progress, long total) {
        ItemStack item = null;
        if (t.nexoItem != null && !t.nexoItem.isBlank()) item = nexoBase(t.nexoItem);
        if (item == null) item = new ItemStack(t.material);

        Map<String, String> ph = placeholders(t, progress, total, null);
        item.editMeta(m -> {
            m.displayName(plugin.text(t.itemName, ph));
            m.lore(buildLore(t, ph));
            m.setUnbreakable(plugin.getConfig().getBoolean("settings.unbreakable", true));
            if (plugin.getConfig().getBoolean("settings.hide-attributes", true)) {
                m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            }
            if (plugin.getConfig().getBoolean("settings.hide-enchantments", true)) {
                m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            for (Map.Entry<Enchantment, Integer> e : t.enchants.entrySet()) {
                m.addEnchant(e.getKey(), e.getValue(), true);
            }
            if (t.itemModel != null && !t.itemModel.isBlank()) {
                NamespacedKey k = NamespacedKey.fromString(t.itemModel.toLowerCase(Locale.ROOT));
                if (k != null) m.setItemModel(k);
                else plugin.getLogger().warning("Palier " + t.id + " : item-model invalide '" + t.itemModel + "'");
            }
            PersistentDataContainer pdc = m.getPersistentDataContainer();
            pdc.set(kTool, PersistentDataType.BYTE, (byte) 1);
            pdc.set(kTier, PersistentDataType.STRING, t.id);
            pdc.set(kProgress, PersistentDataType.LONG, progress);
            pdc.set(kTotal, PersistentDataType.LONG, total);
        });
        // Composant "tool" : l'outil casse TOUT comme pelle + hache + pioche + houe
        item.setData(DataComponentTypes.TOOL, buildTool(t.miningSpeed));
        return item;
    }

    private Tool buildTool(float speed) {
        return toolCache.computeIfAbsent(speed, sp -> Tool.tool()
                .defaultMiningSpeed(1.0f)
                .damagePerBlock(0)
                .addRule(Tool.rule(mineableBlocks(), sp, TriState.TRUE))
                .build());
    }

    private RegistryKeySet<BlockType> mineableBlocks() {
        if (mineable == null) {
            Set<BlockType> set = new HashSet<>();
            for (Material m : Material.values()) {
                if (!m.isBlock() || m.name().startsWith("LEGACY_")) continue;
                if (Tag.MINEABLE_PICKAXE.isTagged(m) || Tag.MINEABLE_AXE.isTagged(m)
                        || Tag.MINEABLE_SHOVEL.isTagged(m) || Tag.MINEABLE_HOE.isTagged(m)) {
                    BlockType bt = m.asBlockType();
                    if (bt != null) set.add(bt);
                }
            }
            mineable = RegistrySet.keySetFromValues(RegistryKey.BLOCK, set);
        }
        return mineable;
    }

    private List<Component> buildLore(Tier t, Map<String, String> ph) {
        List<Component> out = new ArrayList<>();
        for (String line : t.lore) out.add(plugin.text(line, ph));
        return out;
    }

    /** Récupère un item Nexo par réflexion (aucune dépendance de compilation). */
    private ItemStack nexoBase(String id) {
        if (!Bukkit.getPluginManager().isPluginEnabled("Nexo")) {
            if (!nexoWarned) {
                plugin.getLogger().warning("nexo-item configuré mais Nexo n'est pas actif.");
                nexoWarned = true;
            }
            return null;
        }
        try {
            Class<?> c = Class.forName("com.nexomc.nexo.api.NexoItems");
            Method m = c.getMethod("itemFromId", String.class);
            Object target = Modifier.isStatic(m.getModifiers()) ? null : c.getField("INSTANCE").get(null);
            Object builder = m.invoke(target, id);
            if (builder == null) {
                plugin.getLogger().warning("Item Nexo introuvable : " + id);
                return null;
            }
            Method build = builder.getClass().getMethod("build");
            build.setAccessible(true);
            Object res = build.invoke(builder);
            return res instanceof ItemStack is ? is.clone() : null;
        } catch (Throwable ex) {
            plugin.getLogger().warning("Impossible de lire l'item Nexo '" + id + "' : " + ex);
            return null;
        }
    }

    // ------------------------------------------------------------------ état

    public boolean isTool(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(kTool, PersistentDataType.BYTE);
    }

    public State read(ItemStack item) {
        if (tiers.isEmpty() || item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(kTool, PersistentDataType.BYTE)) return null;
        Tier t = tiers.get(pdc.getOrDefault(kTier, PersistentDataType.STRING, ""));
        if (t == null) t = firstTier();
        return new State(t,
                pdc.getOrDefault(kProgress, PersistentDataType.LONG, 0L),
                pdc.getOrDefault(kTotal, PersistentDataType.LONG, 0L));
    }

    /**
     * Ajoute des blocs à la MultiTools tenue en main.
     * @return le palier actuel après traitement, ou null si l'item n'est pas une MultiTools
     */
    public Tier process(Player p, ItemStack hand, long amount) {
        State s = read(hand);
        if (s == null) return null;

        long progress = s.progress() + amount;
        long total = s.total() + amount;
        Tier t = s.tier();
        while (t.next != null && progress >= t.blocksToNext) {
            progress -= t.blocksToNext;
            t = t.next;
        }

        if (t != s.tier()) {
            p.getInventory().setItemInMainHand(create(t, progress, total));
            levelUp(p, s.tier(), t, progress, total);
        } else {
            final Tier ft = t;
            final long fp = progress, fto = total;
            int every = Math.max(1, plugin.getConfig().getInt("settings.lore-update-every", 25));
            boolean refresh = every == 1 || (total / every) != (s.total() / every);
            hand.editMeta(m -> {
                PersistentDataContainer pdc = m.getPersistentDataContainer();
                pdc.set(kProgress, PersistentDataType.LONG, fp);
                pdc.set(kTotal, PersistentDataType.LONG, fto);
                if (refresh) m.lore(buildLore(ft, placeholders(ft, fp, fto, p)));
            });
            // Réécrire l'ItemMeta peut faire perdre le composant "tool" : on le réapplique à chaque bloc
            hand.setData(DataComponentTypes.TOOL, buildTool(ft.miningSpeed));
        }

        if (plugin.getConfig().getBoolean("settings.actionbar-enabled", true)) {
            p.sendActionBar(plugin.msg("actionbar", placeholders(t, progress, total, p)));
        }
        return t;
    }

    private void levelUp(Player p, Tier from, Tier to, long progress, long total) {
        Map<String, String> ph = placeholders(to, progress, total, p);
        ph.put("old_tier", from.name);

        for (String line : plugin.messages().getStringList("level-up.chat")) {
            p.sendMessage(plugin.text(line, ph));
        }
        String title = plugin.messages().getString("level-up.title", "");
        String sub = plugin.messages().getString("level-up.subtitle", "");
        if (!title.isEmpty() || !sub.isEmpty()) {
            List<Integer> tm = plugin.messages().getIntegerList("level-up.title-times");
            if (tm.size() < 3) tm = List.of(10, 70, 20);
            p.showTitle(Title.title(plugin.text(title, ph), plugin.text(sub, ph),
                    Title.Times.times(Ticks.duration(tm.get(0)), Ticks.duration(tm.get(1)), Ticks.duration(tm.get(2)))));
        }
        if (plugin.getConfig().getStringList("settings.broadcast-tiers").contains(to.id)) {
            Bukkit.broadcast(plugin.msg("level-up.broadcast", ph));
        }
        if (to.sound != null && !to.sound.isBlank()) {
            p.playSound(p.getLocation(), to.sound, to.soundVolume, to.soundPitch);
        }
        for (String cmd : to.commands) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    cmd.replace("{player}", p.getName()).replace("{tier_id}", to.id));
        }
    }

    /** Reconstruit les MultiTools de l'inventaire avec la config actuelle (progression conservée). */
    public void refreshInventory(Player p) {
        ItemStack[] c = p.getInventory().getContents();
        boolean changed = false;
        for (int i = 0; i < c.length; i++) {
            State s = read(c[i]);
            if (s != null) {
                c[i] = create(s.tier(), s.progress(), s.total());
                changed = true;
            }
        }
        if (changed) p.getInventory().setContents(c);
    }

    // ------------------------------------------------------------------ placeholders

    public String fmt(long n) { return df.format(n); }

    public Map<String, String> placeholders(Tier t, long progress, long total, Player p) {
        Map<String, String> m = new HashMap<>();
        boolean max = t.next == null;
        String maxText = plugin.messages().getString("max-tier-text", "MAX");
        long needed = Math.max(1, t.blocksToNext);
        double pct = max ? 100.0 : Math.min(100.0, progress * 100.0 / needed);

        m.put("player", p == null ? "" : p.getName());
        m.put("tier", t.name);
        m.put("tier_id", t.id);
        m.put("level", String.valueOf(t.index + 1));
        m.put("max_level", String.valueOf(tiers.size()));
        m.put("progress", fmt(max ? total : progress));
        m.put("needed", max ? maxText : fmt(needed));
        m.put("remaining", max ? maxText : fmt(Math.max(0, needed - progress)));
        m.put("percent", String.valueOf((int) pct));
        m.put("total", fmt(total));
        m.put("next_tier", max ? maxText : t.next.name);
        m.put("bar", bar(pct));
        return m;
    }

    private String bar(double pct) {
        int len = Math.max(1, plugin.getConfig().getInt("settings.progress-bar.length", 20));
        String ch = plugin.getConfig().getString("settings.progress-bar.char", "|");
        String fc = plugin.getConfig().getString("settings.progress-bar.filled-color", "&a");
        String ec = plugin.getConfig().getString("settings.progress-bar.empty-color", "&8");
        int filled = (int) Math.round(pct / 100.0 * len);
        return fc + ch.repeat(filled) + ec + ch.repeat(len - filled);
    }
}
