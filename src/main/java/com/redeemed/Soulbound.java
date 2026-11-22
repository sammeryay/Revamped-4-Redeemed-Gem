package com.redeemed;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

import java.util.Iterator;

/** When soulbound is on: the gem is kept on death and can't be put into chests. */
final class Soulbound implements Listener {
    private final RedeemedGemPlugin pl;

    Soulbound(RedeemedGemPlugin pl) { this.pl = pl; }

    private boolean on() { return pl.getConfig().getBoolean("general.soulbound", true); }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent e) {
        if (!on()) return;
        Iterator<ItemStack> it = e.getDrops().iterator();
        while (it.hasNext()) {
            ItemStack s = it.next();
            if (pl.gems.isRedeemed(s)) {
                it.remove();
                e.getItemsToKeep().add(s);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!on()) return;
        InventoryType top = e.getView().getTopInventory().getType();
        if (top == InventoryType.CRAFTING || top == InventoryType.CREATIVE) return;
        if (e.getView().getTopInventory().getHolder() instanceof MenuListener.DisguiseHolder) return;
        boolean cursorGem = pl.gems.isRedeemed(e.getCursor()) && e.getClickedInventory() == e.getView().getTopInventory();
        boolean shiftGem = e.isShiftClick() && pl.gems.isRedeemed(e.getCurrentItem()) && e.getClickedInventory() == e.getView().getBottomInventory();
        boolean keyGem = false;
        if (e.getClick() == ClickType.NUMBER_KEY && e.getClickedInventory() == e.getView().getTopInventory() && e.getWhoClicked() instanceof Player pp) {
            keyGem = pl.gems.isRedeemed(pp.getInventory().getItem(e.getHotbarButton()));
        }
        if (cursorGem || shiftGem || keyGem) e.setCancelled(true);
    }
}
