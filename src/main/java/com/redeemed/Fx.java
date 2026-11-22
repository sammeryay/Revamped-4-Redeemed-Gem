package com.redeemed;

import org.bukkit.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

final class Fx {
    private Fx() {}

    static double mult() {
        return Math.max(0.0, RedeemedGemPlugin.get().getConfig().getDouble("effects.particle-multiplier", 2.0));
    }

    static int n(int base) {
        return (int) Math.round(base * mult());
    }

    static void dust(Location l, int count, double spread, Color c, float size) {
        int k = n(count);
        if (k <= 0 || l.getWorld() == null) return;
        l.getWorld().spawnParticle(Particle.DUST, l, k, spread, spread, spread, 0, new Particle.DustOptions(c, size));
    }

    static void part(Location l, Particle p, int count, double spread, double speed) {
        int k = n(count);
        if (k <= 0 || l.getWorld() == null) return;
        l.getWorld().spawnParticle(p, l, k, spread, spread, spread, speed);
    }

    static void ring(Location c, double r, Color col, int points, double y) {
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            Location l = c.clone().add(Math.cos(a) * r, y, Math.sin(a) * r);
            dust(l, 1, 0.02, col, 1.4f);
        }
    }

    static void line(Location a, Location b, Color c, int points) {
        for (int i = 0; i <= points; i++) {
            double t = i / (double) points;
            Location l = a.clone().add(b.clone().subtract(a).toVector().multiply(t));
            dust(l, 1, 0.02, c, 1.2f);
        }
    }

    /** True while Set 2 deals its own damage, so its listeners ignore it. */
    static boolean internal = false;

    /** Damage that ignores armor. Kills still credit the source. */
    static void trueDamage(LivingEntity t, double amount, Player src) {
        if (t.isDead() || amount <= 0) return;
        internal = true;
        try {
            double hp = t.getHealth();
            if (hp - amount <= 0.0) {
                t.damage(100000.0, src);
            } else {
                t.setHealth(hp - amount);
                t.playHurtAnimation(0f);
            }
        } finally {
            internal = false;
        }
    }

    /** Removes up to 'steal' durability from each armor piece. Returns the total removed. */
    static int stealDurability(Player t, int steal) {
        ItemStack[] arm = t.getInventory().getArmorContents();
        int total = 0;
        for (ItemStack it : arm) {
            if (it == null || it.getType().getMaxDurability() <= 0) continue;
            if (!(it.getItemMeta() instanceof Damageable d)) continue;
            int max = it.getType().getMaxDurability();
            int take = Math.min(steal, Math.max(0, max - 1 - d.getDamage()));
            if (take <= 0) continue;
            d.setDamage(d.getDamage() + take);
            it.setItemMeta(d);
            total += take;
        }
        t.getInventory().setArmorContents(arm);
        return total;
    }

    static void repair(Player p, int amount) {
        ItemStack[] arm = p.getInventory().getArmorContents();
        for (ItemStack it : arm) {
            if (amount <= 0) break;
            if (it == null || !(it.getItemMeta() instanceof Damageable d)) continue;
            int heal = Math.min(d.getDamage(), amount);
            if (heal <= 0) continue;
            d.setDamage(d.getDamage() - heal);
            it.setItemMeta(d);
            amount -= heal;
        }
        p.getInventory().setArmorContents(arm);
    }
}
