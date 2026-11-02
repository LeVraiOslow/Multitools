package fr.multitools;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Tier {
    public final String id;
    public final int index;
    public String name;
    public String itemName = "MultiTools";
    public String itemModel = "";
    public String nexoItem = "";
    public List<String> lore = List.of();
    public Material material = Material.NETHERITE_PICKAXE;
    public long blocksToNext = 3000;
    public float miningSpeed = 9f;
    public double jobsMultiplier = 1.0;
    public final Map<Enchantment, Integer> enchants = new LinkedHashMap<>();
    public String sound = "";
    public float soundVolume = 1f;
    public float soundPitch = 1f;
    public List<String> commands = List.of();
    public Tier next;

    Tier(String id, int index) {
        this.id = id;
        this.index = index;
        this.name = id;
    }
}
