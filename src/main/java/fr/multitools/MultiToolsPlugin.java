package fr.multitools;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class MultiToolsPlugin extends JavaPlugin {

    private ToolManager tools;
    private JobsRewards jobs;
    private YamlConfiguration messages;
    private final Set<Material> ignored = EnumSet.noneOf(Material.class);
    private final Set<String> disabledWorlds = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveIfMissing("tiers.yml");
        saveIfMissing("messages.yml");

        tools = new ToolManager(this);
        jobs = new JobsRewards(this);
        reloadAll();

        Bukkit.getPluginManager().registerEvents(new ToolListener(this), this);
        PluginCommand cmd = getCommand("multitools");
        if (cmd != null) {
            MultiToolsCommand exec = new MultiToolsCommand(this);
            cmd.setExecutor(exec);
            cmd.setTabCompleter(exec);
        }
    }

    public void reloadAll() {
        reloadConfig();
        messages = loadWithDefaults("messages.yml");
        YamlConfiguration tiers = loadWithDefaults("tiers.yml");

        ignored.clear();
        for (String s : getConfig().getStringList("settings.ignored-blocks")) {
            Material m = Material.matchMaterial(s);
            if (m != null) ignored.add(m);
            else getLogger().warning("ignored-blocks : matériau inconnu " + s);
        }
        disabledWorlds.clear();
        disabledWorlds.addAll(getConfig().getStringList("settings.disabled-worlds"));

        tools.load(tiers);
        jobs.load(getConfig().getConfigurationSection("jobs"));

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (getConfig().getBoolean("settings.refresh-on-join", true)) tools.refreshInventory(p);
        }
    }

    private void saveIfMissing(String name) {
        if (!new File(getDataFolder(), name).exists()) saveResource(name, false);
    }

    /** Charge un yml du dossier du plugin, avec les valeurs du jar en secours pour les clés manquantes. */
    private YamlConfiguration loadWithDefaults(String name) {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(new File(getDataFolder(), name));
        InputStream in = getResource(name);
        if (in != null) {
            cfg.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        }
        return cfg;
    }

    // ------------------------------------------------------------------ helpers

    public ToolManager tools() { return tools; }
    public JobsRewards jobs() { return jobs; }
    public YamlConfiguration messages() { return messages; }
    public boolean isIgnored(Material m) { return ignored.contains(m); }
    public boolean isDisabledWorld(String w) { return disabledWorlds.contains(w); }

    public Component text(String raw, Map<String, String> ph) {
        Map<String, String> map = new HashMap<>();
        if (ph != null) map.putAll(ph);
        map.putIfAbsent("prefix", messages.getString("prefix", ""));
        return Text.parse(raw, map);
    }

    public Component msg(String path, Map<String, String> ph) {
        return text(messages.getString(path, "&cMessage manquant : " + path), ph);
    }
}
