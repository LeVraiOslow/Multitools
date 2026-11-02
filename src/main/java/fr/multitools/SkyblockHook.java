package fr.multitools;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/** Vérifie via réflexion (sans dépendance) si le joueur est membre de l'île où il casse. */
final class SkyblockHook {

    private static boolean init, failed;
    private static Method getPlayer, getIslandAt, isMember;

    private SkyblockHook() {}

    static boolean isIslandMember(MultiToolsPlugin plugin, Player p, Location loc) {
        if (failed || !Bukkit.getPluginManager().isPluginEnabled("SuperiorSkyblock2")) return true; // pas de SSB : on ne bloque pas
        try {
            if (!init) {
                Class<?> api = Class.forName("com.bgsoftware.superiorskyblock.api.SuperiorSkyblockAPI");
                Class<?> sp = Class.forName("com.bgsoftware.superiorskyblock.api.wrappers.SuperiorPlayer");
                Class<?> island = Class.forName("com.bgsoftware.superiorskyblock.api.island.Island");
                getPlayer = api.getMethod("getPlayer", Player.class);
                getIslandAt = api.getMethod("getIslandAt", Location.class);
                isMember = island.getMethod("isMember", sp);
                init = true;
            }
            Object island = getIslandAt.invoke(null, loc);
            if (island == null) return false;
            Object sp = getPlayer.invoke(null, p);
            return (boolean) isMember.invoke(island, sp);
        } catch (Throwable t) {
            failed = true;
            plugin.getLogger().warning("API SuperiorSkyblock2 inutilisable (" + t + "), vérification d'île désactivée.");
            return true;
        }
    }
}
