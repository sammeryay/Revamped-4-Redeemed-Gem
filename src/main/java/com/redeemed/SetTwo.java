package com.redeemed;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.*;

/** Both ability sets, the trust system and soulbound handling. */
final class SetTwo implements Listener {
    private final RedeemedGemPlugin pl;

    private static final class Ch { int used; long end; }
    private final Map<String, Ch> charges = new HashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private final Map<UUID, BukkitTask> pendingSingle = new HashMap<>();
    private final Map<UUID, Long> domainEnd = new HashMap<>();
    private final Map<UUID, Long> lastCrit = new HashMap<>();
    private final Map<UUID, Long> lastFlash = new HashMap<>();
    private final Map<String, int[]> betrayHits = new HashMap<>();
    private final Map<String, Long> betrayLast = new HashMap<>();

    private static final class Domain {
        UUID owner; Location center; double radius; long end;
        final Map<Location, BlockData> saved = new HashMap<>();
    }
    private final List<Domain> domains = new ArrayList<>();

    SetTwo(RedeemedGemPlugin pl) {
        this.pl = pl;
    }

    // ---------- config helpers ----------
    private double d(String p) { return pl.getConfig().getDouble(p); }
    private int i(String p) { return pl.getConfig().getInt(p); }
    private int set() { return pl.abilitySet(); }
    private boolean isGem(ItemStack it) { Gems.Stage st = pl.gems.activeStage(it); return st != null && st.ordinal() >= 1; }
    private int variation() { return pl.getConfig().getInt("set2.slaughter.variation", 1); }

    // ---------- charges / cooldowns ----------
    private Ch ch(UUID id, String k) {
        Ch c = charges.computeIfAbsent(id + ":" + k, x -> new Ch());
        if (c.used > 0 && System.currentTimeMillis() >= c.end) c.used = 0;
        return c;
    }
    private int left(UUID id, String k, int max) { return max - ch(id, k).used; }
    private long remain(UUID id, String k) {
        Ch c = ch(id, k);
        return c.used == 0 ? 0 : Math.max(0, (c.end - System.currentTimeMillis() + 999) / 1000);
    }
    private boolean use(UUID id, String k, int max, double cdSec) {
        Ch c = ch(id, k);
        if (c.used >= max) return false;
        if (c.used == 0) c.end = System.currentTimeMillis() + (long) (cdSec * 1000);
        c.used++;
        return true;
    }
    void resetCooldowns(UUID id) {
        domainEnd.remove(id);
        charges.keySet().removeIf(s -> s.startsWith(id + ":"));
    }

    private void bar(Player p, String s) { p.sendActionBar(Component.text(s, NamedTextColor.YELLOW)); }
    private void onCd(Player p, String name, String key) { bar(p, "On cooldown (" + remain(p.getUniqueId(), key) + "s)"); }

    // ---------- targeting ----------
    boolean valid(Player w, Player t) {
        return t != w && t.isOnline() && !t.isDead()
                && (t.getGameMode() == GameMode.SURVIVAL || t.getGameMode() == GameMode.ADVENTURE)
                && !pl.trust.isTrusted(w.getUniqueId(), t.getUniqueId());
    }

    private Player nearest(Player p, double r) {
        Player best = null;
        double bd = r * r;
        for (Player t : p.getWorld().getPlayers()) {
            if (!valid(p, t)) continue;
            double dd = t.getLocation().distanceSquared(p.getLocation());
            if (dd <= bd) { bd = dd; best = t; }
        }
        return best;
    }

    private List<Player> inRadius(Player p, Location c, double r) {
        List<Player> out = new ArrayList<>();
        for (Player t : c.getWorld().getPlayers()) {
            if (valid(p, t) && t.getLocation().distance(c) <= r) out.add(t);
        }
        return out;
    }

    // ---------- action bar ----------
    void tickActionBar() {
        if (set() != 2) return;
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isGem(p.getInventory().getItemInMainHand()) && !isGem(p.getInventory().getItemInOffHand())) continue;
            UUID id = p.getUniqueId();
            int mc = i("set2.flash-crit.charges");
            String txt = st1(id, "slaughter", now, null)
                    + "  |  " + st1(id, "domain", now, domainEnd)
                    + "  |  " + left(id, "flash", mc) + "/" + mc + st(id, "flash", mc);
            p.sendActionBar(Component.text(txt, NamedTextColor.WHITE));
        }
    }
    private String st(UUID id, String k, int max) {
        return left(id, k, max) == 0 ? " (" + remain(id, k) + "s)" : "";
    }
    private String st1(UUID id, String k, long now, Map<UUID, Long> active) {
        if (active != null && active.getOrDefault(id, 0L) > now) return "ACTIVE";
        long r = remain(id, k);
        return r <= 0 ? "READY" : r + "s";
    }

    // ---------- input ----------
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        EquipmentSlot h = e.getHand();
        Action a = e.getAction();
        if (h == null || a == Action.PHYSICAL) return;
        if (set() != 2) return;
        Player p = e.getPlayer();
        boolean right = a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;
        if (right && h == EquipmentSlot.OFF_HAND && isGem(p.getInventory().getItemInOffHand())) {
            offhandRight(p);
        } else if (right && h == EquipmentSlot.HAND && isGem(p.getInventory().getItemInMainHand())) {
            flash(p);
        } else if (a == Action.LEFT_CLICK_AIR && h == EquipmentSlot.HAND && isGem(p.getInventory().getItemInMainHand())
                && set() == 2 && variation() == 2) {
            slaughterAir(p);
        }
    }

    /** Offhand double right click = Infinite Domain. */
    private void offhandRight(Player p) {
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        long win = pl.getConfig().getLong("general.double-click-ms", 600);
        Long last = lastClick.get(id);
        if (last != null && now - last <= win) {
            lastClick.remove(id);
            domain(p);
            return;
        }
        lastClick.put(id, now);
    }

    // ---------- Set 2: Flash Crit ----------
    private void flash(Player p) {
        long nowMs = System.currentTimeMillis();
        long gap = (long) (d("set2.flash-crit.delay-seconds") * 1000);
        if (nowMs - lastFlash.getOrDefault(p.getUniqueId(), 0L) < gap) return;
        double r = d("set2.flash-crit.radius");
        Player t = nearest(p, r);
        if (t == null) { bar(p, "No untrusted player within " + (int) r + " blocks"); return; }
        if (!use(p.getUniqueId(), "flash", i("set2.flash-crit.charges"), d("set2.flash-crit.cooldown-seconds"))) {
            onCd(p, "Flash Crit", "flash");
            return;
        }
        lastFlash.put(p.getUniqueId(), nowMs);
        Location from = p.getLocation();
        Location tl = t.getLocation();
        Vector back = tl.getDirection().setY(0);
        if (back.lengthSquared() < 1e-4) back = new Vector(1, 0, 0);
        back.normalize().multiply(-1.3);
        Location dest = tl.clone().add(back);
        if (!dest.getBlock().isPassable() || !dest.clone().add(0, 1, 0).getBlock().isPassable()) dest = tl.clone();
        Vector face = tl.toVector().subtract(dest.toVector());
        if (face.lengthSquared() > 1e-4) dest.setDirection(face);
        Fx.line(from.clone().add(0, 1, 0), dest.clone().add(0, 1, 0), Color.BLACK, 24);
        Fx.line(from.clone().add(0, 1.2, 0), dest.clone().add(0, 1.2, 0), Color.YELLOW, 24);
        p.teleport(dest);
        Fx.trueDamage(t, d("set2.flash-crit.damage-hearts") * 2.0, p);
        Fx.part(t.getLocation().add(0, 1, 0), Particle.CRIT, 25, 0.4, 0.3);
        Fx.dust(t.getLocation().add(0, 1, 0), 20, 0.4, Color.YELLOW, 1.4f);
        p.getWorld().playSound(dest, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.8f);
        p.getWorld().playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.4f);
    }

    // ---------- Set 2: Infinite Domain ----------
    private void domain(Player p) {
        if (!use(p.getUniqueId(), "domain", 1, d("set2.infinite-domain.cooldown-seconds"))) { onCd(p, "Infinite Domain", "domain"); return; }
        final int dur = i("set2.infinite-domain.duration-seconds");
        final double r = d("set2.infinite-domain.radius");
        final Domain dm = new Domain();
        dm.owner = p.getUniqueId();
        dm.center = p.getLocation().getBlock().getLocation().add(0.5, 0.5, 0.5);
        dm.radius = r;
        dm.end = System.currentTimeMillis() + dur * 1000L;
        int ir = (int) Math.ceil(r) + 1;
        World w = dm.center.getWorld();
        int cx = dm.center.getBlockX(), cy = dm.center.getBlockY(), cz = dm.center.getBlockZ();
        for (int x = -ir; x <= ir; x++) for (int y = -ir; y <= ir; y++) for (int z = -ir; z <= ir; z++) {
            double dist = Math.sqrt(x * x + y * y + z * z);
            if (dist > r + 0.5 || dist < r - 0.5) continue;
            Block b = w.getBlockAt(cx + x, cy + y, cz + z);
            if (b.getY() < w.getMinHeight() || b.getY() >= w.getMaxHeight()) continue;
            if (b.isEmpty() || b.isLiquid() || b.isReplaceable()) {
                dm.saved.put(b.getLocation(), b.getBlockData());
                b.setType(Material.BLACK_CONCRETE, false);
            }
        }
        domains.add(dm);
        domainEnd.put(p.getUniqueId(), dm.end);
        w.playSound(dm.center, Sound.ENTITY_WITHER_SPAWN, 1f, 0.6f);
        p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, dur * 20 + 10, 2, false, true, true));
        new BukkitRunnable() {
            int ticks = 0;
            @Override public void run() {
                if (System.currentTimeMillis() >= dm.end || !p.isOnline()) { endDomain(dm); cancel(); return; }
                for (Player t : inRadius(p, dm.center, r)) {
                    t.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 50, 0, false, false, true));
                    t.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 50, 0, false, false, true));
                    t.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 50, 0, false, false, true));
                    int slow = i("set2.infinite-domain.slowness-level");
                    if (slow > 0) t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, slow - 1, false, false, true));
                }
                Fx.dust(p.getLocation().add(0, 1, 0), 8, 0.6, Color.YELLOW, 1.2f);
                Fx.dust(dm.center.clone().add(Math.cos(ticks * 0.3) * r * 0.6, 0, Math.sin(ticks * 0.3) * r * 0.6), 6, 0.5, Color.BLACK, 1.5f);
                ticks++;
            }
        }.runTaskTimer(pl, 0L, 5L);
    }

    private void endDomain(Domain dm) {
        if (!domains.remove(dm)) return;
        for (Map.Entry<Location, BlockData> en : dm.saved.entrySet()) en.getKey().getBlock().setBlockData(en.getValue(), false);
        domainEnd.remove(dm.owner);
        dm.center.getWorld().playSound(dm.center, Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.8f);
    }

    private boolean isDomainBlock(Block b) {
        Location l = b.getLocation();
        for (Domain dm : domains) if (dm.saved.containsKey(l)) return true;
        return false;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) { if (isDomainBlock(e.getBlock())) e.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) { e.blockList().removeIf(this::isDomainBlock); }
    @EventHandler(ignoreCancelled = true)
    public void onExplode2(BlockExplodeEvent e) { e.blockList().removeIf(this::isDomainBlock); }
    @EventHandler(ignoreCancelled = true)
    public void onPiston(BlockPistonExtendEvent e) { for (Block b : e.getBlocks()) if (isDomainBlock(b)) { e.setCancelled(true); return; } }
    @EventHandler(ignoreCancelled = true)
    public void onPiston2(BlockPistonRetractEvent e) { for (Block b : e.getBlocks()) if (isDomainBlock(b)) { e.setCancelled(true); return; } }

    // ---------- Set 2: Slaughter ----------
    private void slaughterHit(Player p, Player t) {
        if (!use(p.getUniqueId(), "slaughter", 1, d("set2.slaughter.cooldown-seconds"))) { onCd(p, "Slaughter", "slaughter"); return; }
        bleed(p, t, d("set2.slaughter.damage-hearts"));
    }

    private void slaughterAir(Player p) {
        List<Player> targets = inRadius(p, p.getLocation(), d("set2.slaughter.radius"));
        if (targets.isEmpty()) return;
        if (!use(p.getUniqueId(), "slaughter", 1, d("set2.slaughter.cooldown-seconds"))) { onCd(p, "Slaughter", "slaughter"); return; }
        Fx.ring(p.getLocation(), d("set2.slaughter.radius"), Color.RED, 40, 0.2);
        for (Player t : targets) bleed(p, t, d("set2.slaughter.air-damage-hearts"));
    }

    private void bleed(Player owner, Player t, double hearts) {
        final int dur = i("set2.slaughter.duration-seconds");
        final int steal = i("set2.slaughter.durability-steal");
        new BukkitRunnable() {
            int sec = 0;
            @Override public void run() {
                if (sec >= dur || !t.isOnline() || t.isDead() || !owner.isOnline()) { cancel(); return; }
                Fx.trueDamage(t, hearts * 2.0, owner);
                t.getWorld().playSound(t.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 1.2f);
                t.getWorld().spawnParticle(Particle.BLOCK, t.getLocation().add(0, 1, 0), Math.max(1, Fx.n(15)), 0.3, 0.5, 0.3,
                        Material.REDSTONE_BLOCK.createBlockData());
                if (!t.isDead()) {
                    int stolen = Fx.stealDurability(t, steal);
                    if (stolen > 0) Fx.repair(owner, stolen);
                }
                sec++;
            }
        }.runTaskTimer(pl, 0L, 20L);
    }

    // ---------- damage: trust, auto crit, gem hits ----------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (Fx.internal) return;
        if (!(e.getEntity() instanceof Player victim) || !(e.getDamager() instanceof Player att)) return;
        UUID av = att.getUniqueId(), vv = victim.getUniqueId();
        long now = System.currentTimeMillis();

        // a trusted player hitting the person who trusts them
        if (pl.trust.isTrusted(vv, av)) {
            String k = vv + ":" + av;
            long window = pl.getConfig().getLong("general.trust-hit-window-seconds", 15) * 1000L;
            int[] cnt = betrayHits.computeIfAbsent(k, x -> new int[1]);
            if (now - betrayLast.getOrDefault(k, 0L) > window) cnt[0] = 0;
            betrayLast.put(k, now);
            cnt[0]++;
            if (cnt[0] >= pl.getConfig().getInt("general.trust-break-hits", 5)) {
                pl.trust.remove(vv, av);
                betrayHits.remove(k);
                victim.sendMessage(Component.text(att.getName() + " hit you " + cnt[0] + " times and is no longer trusted.", NamedTextColor.RED));
                att.sendMessage(Component.text("You betrayed " + victim.getName() + " and are no longer trusted.", NamedTextColor.RED));
            }
            return;
        }

        // Infinite Domain auto crit (short delay between crits)
        if (domainEnd.getOrDefault(av, 0L) > now && e.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK
                && now - lastCrit.getOrDefault(av, 0L) >= 800) {
            lastCrit.put(av, now);
            e.setDamage(e.getDamage() * 1.5);
            Fx.part(victim.getLocation().add(0, 1, 0), Particle.CRIT, 20, 0.4, 0.3);
            att.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
        }

        if (!isGem(att.getInventory().getItemInMainHand()) || !valid(att, victim)) return;
        if (set() == 2 && variation() == 1) slaughterHit(att, victim);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        BukkitTask t = pendingSingle.remove(e.getPlayer().getUniqueId());
        if (t != null) t.cancel();
    }

    void shutdown() {
        for (Domain dm : new ArrayList<>(domains)) endDomain(dm);
    }
}
