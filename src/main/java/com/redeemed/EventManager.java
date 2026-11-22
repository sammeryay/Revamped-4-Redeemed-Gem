package com.redeemed;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Team;

import java.util.*;

/** Ritual, Divinity, fragments, Juggernaut, Manhunt and wisps. */
final class EventManager implements Listener {
    private final RedeemedGemPlugin pl;
    private final RitualData data;

    private UUID jug;
    private long jugEnd, jugNext;
    private boolean manhunt;
    private UUID holder;
    private long mhEnd, mhNext;
    private final Set<UUID> exempt = new HashSet<>();
    private final Set<UUID> wisps = new HashSet<>();
    private final Map<UUID, Long> glowUntil = new HashMap<>();
    private double fxT = 0;

    EventManager(RedeemedGemPlugin pl) {
        this.pl = pl;
        this.data = pl.ritual;
    }

    private void say(Component c) { Bukkit.broadcast(c); }
    private Component gold(String s) { return Component.text(s, NamedTextColor.GOLD); }
    private Component divinity(String s) {
        return Component.text("[Divinity] ", NamedTextColor.YELLOW).append(Component.text(s, NamedTextColor.WHITE));
    }
    private int cfgInt(String p) { return pl.getConfig().getInt(p); }
    boolean juggernautActive() { return jug != null; }
    boolean manhuntActive() { return manhunt; }

    // ================= ritual =================
    void giveScroll(Player p) {
        p.getInventory().addItem(RItems.scroll()).values().forEach(x -> p.getWorld().dropItemNaturally(p.getLocation(), x));
        p.sendMessage(divinity("You have been chosen. Use the Redeemed Ritual scroll wisely."));
    }

    void autoGiveScroll() {
        if (data.scrollGiven || !pl.getConfig().getBoolean("ritual.auto-give-scroll", true)) return;
        List<Player> on = new ArrayList<>(Bukkit.getOnlinePlayers());
        on.removeIf(p -> p.getGameMode() == GameMode.SPECTATOR);
        if (on.isEmpty()) return;
        giveScroll(on.get(new Random().nextInt(on.size())));
        data.scrollGiven = true;
        data.save();
    }

    void startRitual(Player p) {
        if (!RItems.is(p.getInventory().getItemInMainHand(), "scroll") && !RItems.is(p.getInventory().getItemInOffHand(), "scroll")) {
            p.sendMessage(Component.text("You need the Redeemed Ritual scroll.", NamedTextColor.RED));
            return;
        }
        if (!pl.getConfig().getIntegerList("allowed-progressions").contains(cfgInt("progression"))) {
            p.sendMessage(Component.text("The ritual can only be used during progression 3 or 4.", NamedTextColor.RED));
            return;
        }
        if (data.ritualActive) {
            p.sendMessage(Component.text("A ritual is already in progress.", NamedTextColor.RED));
            return;
        }
        if (RItems.is(p.getInventory().getItemInMainHand(), "scroll")) p.getInventory().setItemInMainHand(null);
        else p.getInventory().setItemInOffHand(null);
        data.ritualActive = true;
        Arrays.fill(data.placed, false);
        Arrays.fill(data.collector, null);
        data.divinity = p.getLocation().clone();
        award(1, p.getUniqueId(), p.getLocation());
        data.save();
        say(divinity("The Redeemed Ritual has begun. The first fragment has been claimed."));
        data.divinity.getWorld().playSound(data.divinity, Sound.ENTITY_WITHER_SPAWN, 1f, 1.4f);
    }

    void award(int n, UUID uuid, Location fallback) {
        data.collector[n - 1] = uuid;
        ItemStack it = RItems.fragment(n);
        Player p = uuid == null ? null : Bukkit.getPlayer(uuid);
        if (p != null) {
            p.getInventory().addItem(it).values().forEach(x -> p.getWorld().dropItemNaturally(p.getLocation(), x));
            p.sendMessage(divinity("You have received Redeemed Fragment " + n + "."));
        } else if (uuid != null) {
            data.pending.computeIfAbsent(uuid, k -> new ArrayList<>()).add(n);
        } else if (fallback != null) {
            fallback.getWorld().dropItemNaturally(fallback, it);
        }
        data.save();
    }

    private void scanFragments() {
        if (!data.ritualActive || data.divinity == null) return;
        double r = pl.getConfig().getDouble("ritual.fragment-radius", 5);
        for (var ent : data.divinity.getWorld().getNearbyEntities(data.divinity, r, r, r)) {
            if (!(ent instanceof Item item)) continue;
            int n = RItems.fragmentNumber(item.getItemStack());
            if (n == 0 || data.placed[n - 1]) continue;
            data.placed[n - 1] = true;
            item.remove();
            data.save();
            data.divinity.getWorld().playSound(data.divinity, Sound.BLOCK_BEACON_POWER_SELECT, 1.5f, 1f);
            say(divinity("Fragment " + n + " has been placed. (" + placedCount() + "/3)"));
            if (placedCount() == 3) { finishRitual(); return; }
        }
    }

    private int placedCount() {
        int c = 0;
        for (boolean b : data.placed) if (b) c++;
        return c;
    }

    private void finishRitual() {
        Location loc = data.divinity.clone();
        Map<UUID, Integer> weights = new LinkedHashMap<>();
        for (UUID u : data.collector) if (u != null) weights.merge(u, 1, Integer::sum);
        UUID winner = null;
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        if (total > 0) {
            int roll = new Random().nextInt(total);
            for (Map.Entry<UUID, Integer> en : weights.entrySet()) {
                roll -= en.getValue();
                if (roll < 0) { winner = en.getKey(); break; }
            }
        }
        String name = winner == null ? "no one" : Optional.ofNullable(Bukkit.getOfflinePlayer(winner).getName()).orElse("Unknown");
        say(divinity("All three fragments are united. The Redeemed Gem belongs to " + name + "!"));
        World w = loc.getWorld();
        Location c = loc.clone().add(0, 1.6, 0);
        w.playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.8f);
        w.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2f, 1f);
        w.spawnParticle(Particle.EXPLOSION_EMITTER, c, 2);
        w.spawnParticle(Particle.FIREWORK, c, 250, 0.3, 0.3, 0.3, 0.45);
        w.spawnParticle(Particle.END_ROD, c, 120, 0.3, 0.3, 0.3, 0.35);
        w.spawnParticle(Particle.DUST, c, 200, 0.4, 0.4, 0.4, 0.5, new Particle.DustOptions(Color.YELLOW, 2f));
        w.spawnParticle(Particle.DUST, c, 200, 0.4, 0.4, 0.4, 0.5, new Particle.DustOptions(Color.BLACK, 2f));
        Player wp = winner == null ? null : Bukkit.getPlayer(winner);
        if (wp != null) pl.grantGem(wp);
        else w.dropItemNaturally(loc.clone().add(0, 1, 0), pl.gems.create(Gems.Stage.AWAKENED, null));
        data.resetRitual();
    }

    // ================= juggernaut =================
    void startJuggernaut(Player target) {
        if (jug != null) stopJuggernaut(false);
        jug = target.getUniqueId();
        int mins = cfgInt("events.juggernaut-minutes");
        jugEnd = mins <= 0 ? 0 : System.currentTimeMillis() + mins * 60000L;
        jugNext = System.currentTimeMillis() + intervalMs();
        boolean has = false;
        for (ItemStack s : target.getInventory().getContents()) if (RItems.is(s, "compass")) has = true;
        if (!has) target.getInventory().addItem(RItems.compass()).values().forEach(x -> target.getWorld().dropItemNaturally(target.getLocation(), x));
        say(divinity(target.getName() + " is the Juggernaut. Hunt them down for the second fragment!"));
        reveal(target, "The Juggernaut");
    }

    void stopJuggernaut(boolean announce) {
        if (jug == null) return;
        Player p = Bukkit.getPlayer(jug);
        jug = null;
        restoreLocator();
        if (p != null) removeGlow(p);
        if (announce) say(divinity("The Juggernaut event has ended."));
    }

    private void applyLocator() {
        Attribute rr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("waypoint_receive_range"));
        if (rr == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            AttributeInstance ai = p.getAttribute(rr);
            if (ai == null) continue;
            boolean has = false;
            for (ItemStack s : p.getInventory().getContents()) if (RItems.is(s, "compass")) { has = true; break; }
            ai.setBaseValue(has ? ai.getDefaultValue() : 0.0);
        }
    }

    private void restoreLocator() {
        Attribute rr = Registry.ATTRIBUTE.get(NamespacedKey.minecraft("waypoint_receive_range"));
        if (rr == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            AttributeInstance ai = p.getAttribute(rr);
            if (ai != null) ai.setBaseValue(ai.getDefaultValue());
        }
    }

    // ================= manhunt =================
    boolean startManhunt() {
        List<Player> pool = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers())
            if (!exempt.contains(p.getUniqueId()) && p.getGameMode() != GameMode.SPECTATOR) pool.add(p);
        if (pool.isEmpty()) return false;
        manhunt = true;
        mhEnd = System.currentTimeMillis() + cfgInt("events.manhunt-minutes") * 60000L;
        mhNext = System.currentTimeMillis() + intervalMs();
        giveStatus(pool.get(new Random().nextInt(pool.size())), true);
        say(divinity("The Manhunt has begun! Whoever holds the Redeemed status must survive."));
        return true;
    }

    void giveStatus(Player p, boolean first) {
        if (holder != null) {
            Player old = Bukkit.getPlayer(holder);
            if (old != null) { removeGlow(old); old.playerListName(null); }
        }
        holder = p.getUniqueId();
        p.playerListName(Component.text(p.getName(), NamedTextColor.YELLOW));
        p.sendMessage(divinity("You now hold the Redeemed status."));
        if (!first) say(divinity("The Redeemed status has changed hands."));
        reveal(p, "The Redeemed status holder");
    }

    void stopManhunt() {
        manhunt = false;
        if (holder != null) {
            Player p = Bukkit.getPlayer(holder);
            if (p != null) { removeGlow(p); p.playerListName(null); }
        }
        holder = null;
    }

    private void endManhunt() {
        UUID last = holder;
        say(divinity("The Manhunt is Over."));
        stopManhunt();
        if (last != null) {
            award(3, last, null);
            say(divinity(Optional.ofNullable(Bukkit.getOfflinePlayer(last).getName()).orElse("Someone") + " survived and receives the third fragment."));
        }
    }

    void exempt(UUID u) { exempt.add(u); }

    // ================= reveal / glow =================
    private long intervalMs() { return Math.max(5, cfgInt("events.reveal-interval-seconds")) * 1000L; }

    private void reveal(Player p, String who) {
        glow(p, Math.max(1, cfgInt("events.reveal-glow-seconds")));
        if (pl.getConfig().getBoolean("events.reveal-coordinates", true)) {
            Location l = p.getLocation();
            boolean showName = !who.startsWith("The Redeemed") || pl.getConfig().getBoolean("events.reveal-manhunt-name", true);
            String label = showName ? who + " (" + p.getName() + ")" : who;
            say(divinity(label + " is at " + l.getBlockX() + ", " + l.getBlockY() + ", " + l.getBlockZ() + " in " + l.getWorld().getName() + "."));
        }
    }

    private Team team() {
        Team t = Bukkit.getScoreboardManager().getMainScoreboard().getTeam("rg_yellow");
        if (t == null) t = Bukkit.getScoreboardManager().getMainScoreboard().registerNewTeam("rg_yellow");
        t.color(NamedTextColor.YELLOW);
        return t;
    }

    private void glow(Player p, int seconds) {
        if (!pl.getConfig().getBoolean("events.glowing", true)) return;
        team().addEntry(p.getName());
        p.setGlowing(true);
        long until = System.currentTimeMillis() + seconds * 1000L;
        glowUntil.put(p.getUniqueId(), until);
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (glowUntil.getOrDefault(p.getUniqueId(), 0L) <= System.currentTimeMillis()) removeGlow(p);
        }, seconds * 20L + 2L);
    }

    private void removeGlow(Player p) {
        glowUntil.remove(p.getUniqueId());
        p.setGlowing(false);
        team().removeEntry(p.getName());
    }

    // ================= wisps =================
    void setWisp(UUID u, boolean on) { if (on) wisps.add(u); else wisps.remove(u); }

    private void orb(Location c, double t, double rad) {
        Color[] cols = {Color.YELLOW, Color.BLACK, Color.WHITE};
        for (int i = 0; i < 6; i++) {
            double a = t * 0.18 + i * Math.PI / 3;
            double y = Math.sin(t * 0.12 + i) * 0.35;
            Fx.dust(c.clone().add(Math.cos(a) * rad, y, Math.sin(a) * rad), 1, 0.03, cols[i % 3], 1.1f);
        }
        Fx.dust(c, 3, 0.18, Color.YELLOW, 1.6f);
        Fx.dust(c, 3, 0.18, Color.BLACK, 1.6f);
        Fx.part(c, Particle.END_ROD, 1, 0.15, 0.01);
    }

    /** Called every 3 ticks. */
    void fxTick() {
        fxT += 1;
        if (data.ritualActive && data.divinity != null && data.divinity.getWorld() != null) {
            Location c = data.divinity.clone().add(0, 1.8 + Math.sin(fxT * 0.1) * 0.2, 0);
            orb(c, fxT, 0.9);
            for (int i = 0; i < 3; i++) {
                double a = i * 2 * Math.PI / 3;
                Location o = data.divinity.clone().add(Math.cos(a) * 3.5, 0.1, Math.sin(a) * 3.5);
                if (data.placed[i]) {
                    for (double y = 0; y < 2.5; y += 0.5) Fx.dust(o.clone().add(0, y, 0), 1, 0.05, Color.YELLOW, 1.3f);
                } else {
                    Fx.ring(o, 0.5, Color.GRAY, 8, 0.05);
                }
            }
        }
        for (UUID u : wisps) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) orb(p.getLocation().add(0, 2.4, 0), fxT, 0.6);
        }
    }

    // ================= per-second tick =================
    void tick() {
        long now = System.currentTimeMillis();
        autoGiveScroll();
        scanFragments();
        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean[] f = new boolean[4];
            for (ItemStack s : p.getInventory().getContents()) {
                int n = RItems.fragmentNumber(s);
                if (n > 0) f[n] = true;
            }
            if (f[1]) p.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 80, 0, true, false, true));
            if (f[2]) p.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 80, 0, true, false, true));
            if (f[3]) p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 80, 0, true, false, true));
        }
        if (jug != null) {
            Player p = Bukkit.getPlayer(jug);
            if (jugEnd > 0 && now >= jugEnd) stopJuggernaut(true);
            else {
                applyLocator();
                if (now >= jugNext) { jugNext = now + intervalMs(); if (p != null) reveal(p, "The Juggernaut"); }
            }
        }
        if (manhunt) {
            if (now >= mhEnd) endManhunt();
            else if (now >= mhNext) {
                mhNext = now + intervalMs();
                Player p = holder == null ? null : Bukkit.getPlayer(holder);
                if (p != null) reveal(p, "The Redeemed status holder");
            }
        }
    }

    // ================= listeners =================
    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player v = e.getEntity();
        Player k = v.getKiller();
        if (jug != null && v.getUniqueId().equals(jug)) {
            stopJuggernaut(false);
            say(divinity("The Juggernaut has fallen!"));
            award(2, k == null ? null : k.getUniqueId(), v.getLocation());
        }
        if (manhunt && holder != null && v.getUniqueId().equals(holder) && k != null && !exempt.contains(k.getUniqueId())) {
            giveStatus(k, false);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        List<Integer> pend = data.pending.remove(p.getUniqueId());
        if (pend != null) {
            for (int n : pend) p.getInventory().addItem(RItems.fragment(n)).values().forEach(x -> p.getWorld().dropItemNaturally(p.getLocation(), x));
            data.save();
        }
        if (holder != null && p.getUniqueId().equals(holder)) p.playerListName(Component.text(p.getName(), NamedTextColor.YELLOW));
    }

    void shutdown() {
        restoreLocator();
        for (UUID u : new ArrayList<>(glowUntil.keySet())) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) removeGlow(p);
        }
        if (holder != null) {
            Player h = Bukkit.getPlayer(holder);
            if (h != null) h.playerListName(null);
        }
    }
}
