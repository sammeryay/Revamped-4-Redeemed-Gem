package com.redeemed;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

final class RItems {
    private RItems() {}

    static NamespacedKey key() {
        return new NamespacedKey(RedeemedGemPlugin.get(), "rg_item");
    }

    static Component t(String s, TextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }

    private static ItemStack tag(ItemStack i, ItemMeta m, String id) {
        m.getPersistentDataContainer().set(key(), PersistentDataType.STRING, id);
        i.setItemMeta(m);
        return i;
    }

    static String id(ItemStack i) {
        if (i == null || !i.hasItemMeta()) return null;
        return i.getItemMeta().getPersistentDataContainer().get(key(), PersistentDataType.STRING);
    }

    static boolean is(ItemStack i, String want) {
        return want.equals(id(i));
    }

    /** Returns 1-3 for a fragment, 0 otherwise. */
    static int fragmentNumber(ItemStack i) {
        String s = id(i);
        if (s == null || !s.startsWith("frag")) return 0;
        try {
            return Integer.parseInt(s.substring(4));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    static ItemStack scroll() {
        ItemStack i = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta m = (BookMeta) i.getItemMeta();
        m.title(Component.text("Redeemed Events"));
        m.author(Component.text("Divinity"));
        Component page = Component.text("Redeemed Events\n\n", NamedTextColor.GOLD, TextDecoration.BOLD)
                .append(Component.text("[ START ]", NamedTextColor.DARK_GREEN, TextDecoration.BOLD)
                        .clickEvent(ClickEvent.runCommand("/rg ritual start")));
        m.addPages(page);
        m.displayName(t("Redeemed Ritual Scroll", NamedTextColor.GOLD));
        m.lore(List.of(t("Usable during progression 3 or 4.", NamedTextColor.GRAY),
                t("Right click, then press START.", NamedTextColor.GRAY)));
        return tag(i, m, "scroll");
    }

    static ItemStack fragment(int n) {
        Material mat = switch (n) {
            case 1 -> Material.EMERALD;
            case 2 -> Material.BLAZE_POWDER;
            default -> Material.NETHERITE_SCRAP;
        };
        String effect = switch (n) {
            case 1 -> "Infinite Hero of the Village";
            case 2 -> "Infinite Strength I";
            default -> "Infinite Resistance I";
        };
        ItemStack i = new ItemStack(mat);
        ItemMeta m = i.getItemMeta();
        m.displayName(t("Redeemed Fragment " + "I".repeat(n), NamedTextColor.YELLOW));
        m.lore(List.of(t(effect, NamedTextColor.GRAY), t("Drop it near Divinity to place it.", NamedTextColor.DARK_GRAY)));
        m.setEnchantmentGlintOverride(true);
        return tag(i, m, "frag" + n);
    }

    static ItemStack compass() {
        ItemStack i = new ItemStack(Material.COMPASS);
        ItemMeta m = i.getItemMeta();
        m.displayName(t("Player's Compass", NamedTextColor.GOLD));
        m.lore(List.of(t("Shows every player on the locator bar.", NamedTextColor.GRAY),
                t("Keep it in your inventory.", NamedTextColor.DARK_GRAY)));
        m.addEnchant(Enchantment.VANISHING_CURSE, 1, true);
        return tag(i, m, "compass");
    }
}
