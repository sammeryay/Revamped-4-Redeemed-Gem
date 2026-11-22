package com.redeemed;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

/** Trust list, ritual progress and pending fragment grants, saved to ritual.yml. */
final class RitualData {
    private final RedeemedGemPlugin pl;
    private final File file;

    final Map<UUID, List<Integer>> pending = new HashMap<>();
    boolean scrollGiven;
    boolean ritualActive;
    Location divinity;
    final boolean[] placed = new boolean[3];
    final UUID[] collector = new UUID[3];

    RitualData(RedeemedGemPlugin pl) {
        this.pl = pl;
        this.file = new File(pl.getDataFolder(), "ritual.yml");
    }

    void load() {
        pending.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        scrollGiven = y.getBoolean("scroll-given");
        ritualActive = y.getBoolean("ritual.active");
        String w = y.getString("ritual.world");
        if (w != null) {
            World world = Bukkit.getWorld(w);
            if (world != null) divinity = new Location(world, y.getDouble("ritual.x"), y.getDouble("ritual.y"), y.getDouble("ritual.z"));
        }
        for (int i = 0; i < 3; i++) {
            placed[i] = y.getBoolean("ritual.placed." + i);
            String c = y.getString("ritual.collector." + i);
            collector[i] = c == null ? null : UUID.fromString(c);
        }
        ConfigurationSection p = y.getConfigurationSection("pending");
        if (p != null) for (String u : p.getKeys(false)) pending.put(UUID.fromString(u), new ArrayList<>(p.getIntegerList(u)));
    }

    void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("scroll-given", scrollGiven);
        y.set("ritual.active", ritualActive);
        if (divinity != null && divinity.getWorld() != null) {
            y.set("ritual.world", divinity.getWorld().getName());
            y.set("ritual.x", divinity.getX());
            y.set("ritual.y", divinity.getY());
            y.set("ritual.z", divinity.getZ());
        }
        for (int i = 0; i < 3; i++) {
            y.set("ritual.placed." + i, placed[i]);
            y.set("ritual.collector." + i, collector[i] == null ? null : collector[i].toString());
        }
        for (Map.Entry<UUID, List<Integer>> e : pending.entrySet()) y.set("pending." + e.getKey(), e.getValue());
        try {
            pl.getDataFolder().mkdirs();
            y.save(file);
        } catch (IOException ex) {
            pl.getLogger().warning("Could not save ritual.yml: " + ex.getMessage());
        }
    }

    void resetRitual() {
        ritualActive = false;
        divinity = null;
        Arrays.fill(placed, false);
        Arrays.fill(collector, null);
        save();
    }
}
