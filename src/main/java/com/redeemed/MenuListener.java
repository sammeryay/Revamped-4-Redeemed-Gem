package com.redeemed;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** The disguise menu. (The config menu is now a set of dialogs, see ConfigMenus.) */
public final class MenuListener implements Listener {
    static final class DisguiseHolder implements InventoryHolder {
        public Inventory getInventory() { return null; }
    }

    private final RedeemedGemPlugin pl;

    public MenuListener(RedeemedGemPlugin pl) { this.pl = pl; }

    private static final int[] GEM_SLOTS = {10, 11, 12, 13, 14, 15, 16, 17};
    private static final int REVERT_SLOT = 22;

    public void openDisguise(Player p) {
        Inventory inv = Bukkit.createInventory(new DisguiseHolder(), 27, Component.text("Disguise your gem"));
        ItemStack pane = named(new ItemStack(Material.GRAY_STAINED_GLASS_PANE), " ");
        for (int i = 0; i < 27; i++) inv.setItem(i, pane);
        for (int i = 0; i < Gems.DISGUISES.length; i++) {
            ItemStack it = pl.gems.create(Gems.Stage.DORMANT, Gems.DISGUISES[i]);
            ItemMeta m = it.getItemMeta();
            m.lore(List.of(Component.text("Click to disguise as this gem.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            it.setItemMeta(m);
            inv.setItem(GEM_SLOTS[i], it);
        }
        Gems.Stage cur = pl.gems.stageInInventory(p);
        ItemStack revert = pl.gems.create(cur == null ? Gems.Stage.DORMANT : cur, null);
        ItemMeta rm = revert.getItemMeta();
        rm.lore(List.of(Component.text("Click to turn back into the Redeemed Gem.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        revert.setItemMeta(rm);
        inv.setItem(REVERT_SLOT, revert);
        p.openInventory(inv);
    }

    private boolean soulbound() { return pl.getConfig().getBoolean("general.soulbound", true); }

    /** Soulbound: dropping the gem opens the disguise menu. Not soulbound: it drops like any item (use /rg disguise). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (!soulbound() || !pl.gems.isRedeemed(e.getItemDrop().getItemStack())) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTask(pl, () -> openDisguise(p));
    }

    private static ItemStack named(ItemStack it, String name) {
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(name));
        it.setItemMeta(m);
        return it;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        Inventory top = e.getView().getTopInventory();
        Player p = (Player) e.getWhoClicked();

        if (!(top.getHolder() instanceof DisguiseHolder)) {
            if (soulbound() && (e.getClick() == ClickType.DROP || e.getClick() == ClickType.CONTROL_DROP) && pl.gems.isRedeemed(e.getCurrentItem())) {
                e.setCancelled(true);
                Bukkit.getScheduler().runTask(pl, () -> openDisguise(p));
            }
            return;
        }
        e.setCancelled(true);
        if (e.getClickedInventory() != top) return;
        int slot = e.getSlot();
        if (slot == REVERT_SLOT) {
            pl.gems.setDisguise(p, null);
            p.sendMessage(Component.text("Your gem is the Redeemed Gem again.", NamedTextColor.GRAY));
        } else {
            for (int i = 0; i < GEM_SLOTS.length; i++) {
                if (GEM_SLOTS[i] == slot) {
                    pl.gems.setDisguise(p, Gems.DISGUISES[i]);
                    p.sendMessage(Component.text("Your gem now looks like the " + Gems.cap(Gems.DISGUISES[i]) + " Gem.", NamedTextColor.GRAY));
                }
            }
        }
        if (slot == REVERT_SLOT || contains(GEM_SLOTS, slot)) {
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
            p.closeInventory();
        }
    }

    private static boolean contains(int[] a, int v) { for (int x : a) if (x == v) return true; return false; }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof DisguiseHolder) e.setCancelled(true);
    }
}
