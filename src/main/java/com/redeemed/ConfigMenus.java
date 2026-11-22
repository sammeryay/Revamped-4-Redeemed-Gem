package com.redeemed;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/** /rg config: dialog menus with checkboxes and sliders. */
final class ConfigMenus {
    private final RedeemedGemPlugin pl;

    private record S(String path, String label, float min, float max, float step) {}

    private static final List<S> SET1 = List.of(
            new S("shackles.charges", "Shackles: charges", 1f, 10f, 1f),
            new S("shackles.ground-seconds", "Shackles: ground time (s)", 0.5f, 10f, 0.1f),
            new S("shackles.damage-hearts", "Shackles: true damage (hearts)", 0f, 20f, 0.5f),
            new S("shackles.recharge-seconds", "Shackles: recharge (s)", 5f, 600f, 5f),
            new S("slam.charges", "Shattered Slam: charges", 1f, 10f, 1f),
            new S("slam.radius", "Shattered Slam: radius", 2f, 30f, 1f),
            new S("slam.damage-hearts", "Shattered Slam: true damage (hearts)", 0f, 20f, 0.5f),
            new S("slam.fling-distance", "Shattered Slam: fling distance", 0f, 40f, 1f),
            new S("slam.cooldown-seconds", "Shattered Slam: cooldown (s)", 5f, 600f, 5f),
            new S("limitless.radius", "Limitless: radius", 2f, 30f, 1f),
            new S("limitless.hearts-taken", "Limitless: hearts taken", 0f, 20f, 0.5f),
            new S("limitless.hearts-given", "Limitless: hearts given", 0f, 20f, 0.5f),
            new S("limitless.lock-radius", "Limitless: lock radius", 0.5f, 10f, 0.5f),
            new S("limitless.lock-seconds", "Limitless: lock time (s)", 1f, 60f, 1f),
            new S("limitless.duration-seconds", "Limitless: duration (s)", 1f, 120f, 1f),
            new S("limitless.cooldown-seconds", "Limitless: cooldown (s)", 5f, 600f, 5f),
            new S("limitless.armor-mend-per-second", "Limitless: armor mend / sec", 0f, 50f, 1f));

    private static final List<S> SET2 = List.of(
            new S("set2.flash-crit.damage-hearts", "Flash Crit: damage (hearts)", 0.5f, 10f, 0.5f),
            new S("set2.flash-crit.radius", "Flash Crit: radius", 3f, 25f, 1f),
            new S("set2.flash-crit.charges", "Flash Crit: charges", 1f, 10f, 1f),
            new S("set2.flash-crit.delay-seconds", "Flash Crit: delay between uses (s)", 0f, 5f, 0.1f),
            new S("set2.flash-crit.cooldown-seconds", "Flash Crit: cooldown (s)", 10f, 300f, 5f),
            new S("set2.infinite-domain.radius", "Infinite Domain: radius", 3f, 15f, 1f),
            new S("set2.infinite-domain.slowness-level", "Infinite Domain: slowness level (0 = off)", 0f, 5f, 1f),
            new S("set2.infinite-domain.duration-seconds", "Infinite Domain: duration (s)", 1f, 30f, 1f),
            new S("set2.infinite-domain.cooldown-seconds", "Infinite Domain: cooldown (s)", 10f, 300f, 5f),
            new S("set2.slaughter.damage-hearts", "Slaughter (hit): hearts per second", 0.5f, 5f, 0.5f),
            new S("set2.slaughter.air-damage-hearts", "Slaughter (air): hearts per second", 0.5f, 5f, 0.5f),
            new S("set2.slaughter.radius", "Slaughter: radius", 2f, 15f, 1f),
            new S("set2.slaughter.duration-seconds", "Slaughter: duration (s)", 1f, 30f, 1f),
            new S("set2.slaughter.cooldown-seconds", "Slaughter: cooldown (s)", 10f, 300f, 5f));

    ConfigMenus(RedeemedGemPlugin pl) { this.pl = pl; }

    // ---------- helpers ----------
    private static final ClickCallback.Options OPTS = ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES).lifetime(Duration.ofMinutes(15)).build();

    private ActionButton btn(String label, int width, BiConsumer<DialogResponseView, Audience> cb) {
        return ActionButton.create(Component.text(label), null, width, DialogAction.customClick((v, a) -> cb.accept(v, a), OPTS));
    }

    private Dialog dialog(String title, String info, List<DialogInput> inputs, DialogType type) {
        List<DialogBody> body = new ArrayList<>();
        if (info != null && !info.isEmpty()) body.add(DialogBody.plainMessage(Component.text(info)));
        return Dialog.create(f -> f.empty()
                .base(DialogBase.builder(Component.text(title, NamedTextColor.GOLD)).body(body).inputs(inputs).build())
                .type(type));
    }

    private DialogInput slider(String key, String label, float min, float max, float step, double cur) {
        float c = (float) Math.max(min, Math.min(max, cur));
        return DialogInput.numberRange(key, Component.text(label), min, max).step(step).initial(c).build();
    }

    private DialogInput check(String key, String label, boolean cur) {
        return DialogInput.bool(key, Component.text(label)).initial(cur).build();
    }

    private float f(DialogResponseView v, String k, float def) {
        Float x = v.getFloat(k);
        return x == null ? def : x;
    }

    private boolean b(DialogResponseView v, String k, boolean def) {
        Boolean x = v.getBoolean(k);
        return x == null ? def : x;
    }

    private double snap(float v, float step) {
        return Math.round(Math.round(v / step) * step * 100.0) / 100.0;
    }

    private void saved(Player p) {
        pl.saveConfig();
        p.sendMessage(Component.text("Redeemed Gem config saved.", NamedTextColor.GREEN));
    }

    // ---------- main menu ----------
    void open(Player p) {
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(btn("Gem Abilities", 220, (v, a) -> abilities(p)));
        buttons.add(btn("Passives", 220, (v, a) -> passives(p)));
        buttons.add(btn("General Gem Settings", 220, (v, a) -> general(p)));
        buttons.add(btn("Events", 220, (v, a) -> events(p)));
        ActionButton close = btn("Close", 220, (v, a) -> {});
        p.showDialog(dialog("Redeemed Gem Config", "Pick a category.", new ArrayList<>(), DialogType.multiAction(buttons, close, 1)));
    }

    // ---------- gem abilities ----------
    private void abilities(Player p) {
        FileConfiguration c = pl.getConfig();
        List<DialogInput> in = new ArrayList<>();
        in.add(check("set2", "Use Set 2 (revamped abilities). Unchecked = Set 1 (original).", c.getInt("ability-set", 2) == 2));
        in.add(check("var2", "Slaughter: variation 2 (swing at the air). Unchecked = variation 1 (hit a player).", c.getInt("set2.slaughter.variation", 1) == 2));
        ActionButton next = btn("Save & edit values", 180, (v, a) -> {
            c.set("ability-set", b(v, "set2", true) ? 2 : 1);
            c.set("set2.slaughter.variation", b(v, "var2", false) ? 2 : 1);
            pl.saveConfig();
            pl.refreshGems();
            values(p);
        });
        ActionButton back = btn("Back", 100, (v, a) -> open(p));
        p.showDialog(dialog("Gem Abilities", "Set 1: Shackles, Shattered Slam, Limitless. Set 2: Flash Crit, Infinite Domain, Slaughter.", in, DialogType.confirmation(next, back)));
    }

    private void values(Player p) {
        FileConfiguration c = pl.getConfig();
        boolean set2 = c.getInt("ability-set", 2) == 2;
        List<S> specs = set2 ? SET2 : SET1;
        List<DialogInput> in = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            S s = specs.get(i);
            in.add(slider("s" + i, s.label(), s.min(), s.max(), s.step(), c.getDouble(s.path())));
        }
        ActionButton save = btn("Save", 120, (v, a) -> {
            for (int i = 0; i < specs.size(); i++) {
                S s = specs.get(i);
                c.set(s.path(), snap(f(v, "s" + i, (float) c.getDouble(s.path())), s.step()));
            }
            saved(p);
            open(p);
        });
        ActionButton back = btn("Back", 100, (v, a) -> abilities(p));
        p.showDialog(dialog(set2 ? "Set 2 Values" : "Set 1 Values", "Editing the " + (set2 ? "revamped" : "original") + " ability set.", in, DialogType.confirmation(save, back)));
    }

    // ---------- passives ----------
    private static final List<S> PASSIVES = List.of(
            new S("awakened.bonus-hearts", "Bonus hearts (Awakened gem)", 0f, 40f, 1f),
            new S("awakened.resistance-level", "Constant Resistance level (0 = off)", 0f, 5f, 1f),
            new S("awakened.speed-level", "Constant Speed level (0 = off)", 0f, 5f, 1f),
            new S("awakened.strength-level", "Constant Strength level (0 = off)", 0f, 5f, 1f),
            new S("awakened.regeneration-level", "Constant Regeneration level (0 = off)", 0f, 5f, 1f),
            new S("passive.darkness-every-hits", "Darkness every N hits", 1f, 50f, 1f),
            new S("passive.darkness-seconds", "Darkness time (s)", 1f, 30f, 1f),
            new S("passive.crit-chance", "Crit chance (0.05 = 1 in 20)", 0f, 1f, 0.01f),
            new S("passive.crit-strength-level", "Crit strength level", 0f, 10f, 1f));

    private void passives(Player p) {
        FileConfiguration c = pl.getConfig();
        List<DialogInput> in = new ArrayList<>();
        in.add(check("dark", "Darkness passive (every Nth hit)", c.getBoolean("passive.darkness-enabled", true)));
        in.add(check("crit", "Crit passive (chance of a Strength hit)", c.getBoolean("passive.crit-enabled", true)));
        for (int i = 0; i < PASSIVES.size(); i++) {
            S s = PASSIVES.get(i);
            in.add(slider("s" + i, s.label(), s.min(), s.max(), s.step(), c.getDouble(s.path())));
        }
        ActionButton save = btn("Save", 120, (v, a) -> {
            c.set("passive.darkness-enabled", b(v, "dark", true));
            c.set("passive.crit-enabled", b(v, "crit", true));
            for (int i = 0; i < PASSIVES.size(); i++) {
                S s = PASSIVES.get(i);
                c.set(s.path(), snap(f(v, "s" + i, (float) c.getDouble(s.path())), s.step()));
            }
            saved(p);
            open(p);
        });
        ActionButton back = btn("Back", 100, (v, a) -> open(p));
        p.showDialog(dialog("Passives", "Passives work with either ability set. Constant effects need an Awakened gem in the inventory.", in, DialogType.confirmation(save, back)));
    }

    // ---------- general ----------
    private void general(Player p) {
        FileConfiguration c = pl.getConfig();
        List<DialogInput> in = new ArrayList<>();
        in.add(check("soul", "Soulbound gem (cannot be dropped, kept on death)", c.getBoolean("general.soulbound", true)));
        in.add(slider("part", "Particle amount (x)", 0f, 2f, 0.1f, c.getDouble("effects.particle-multiplier", 2.0)));
        in.add(slider("dbl", "Double-click window (ms)", 300f, 1000f, 50f, c.getDouble("general.double-click-ms", 600)));
        in.add(slider("hits", "Hits from a trusted player to auto-untrust", 2f, 10f, 1f, c.getDouble("general.trust-break-hits", 5)));
        ActionButton save = btn("Save", 120, (v, a) -> {
            c.set("general.soulbound", b(v, "soul", true));
            c.set("effects.particle-multiplier", snap(f(v, "part", 2f), 0.1f));
            c.set("general.double-click-ms", (int) snap(f(v, "dbl", 600f), 50f));
            c.set("general.trust-break-hits", (int) snap(f(v, "hits", 5f), 1f));
            saved(p);
            open(p);
        });
        ActionButton back = btn("Back", 100, (v, a) -> open(p));
        p.showDialog(dialog("General Gem Settings", null, in, DialogType.confirmation(save, back)));
    }

    // ---------- events ----------
    private void events(Player p) {
        FileConfiguration c = pl.getConfig();
        int jm = c.getInt("events.juggernaut-minutes", 60);
        List<DialogInput> in = new ArrayList<>();
        in.add(slider("jug", "Juggernaut duration (min). Max = unlimited", 30f, 360f, 10f, jm <= 0 ? 360 : jm));
        in.add(slider("man", "Manhunt duration (min)", 30f, 120f, 5f, c.getDouble("events.manhunt-minutes", 120)));
        in.add(check("glow", "Glowing effect on reveal", c.getBoolean("events.glowing", true)));
        in.add(slider("int", "Reveal every (seconds)", 15f, 300f, 15f, c.getDouble("events.reveal-interval-seconds", 60)));
        in.add(slider("dur", "Glow lasts (seconds)", 3f, 30f, 1f, c.getDouble("events.reveal-glow-seconds", 10)));
        in.add(check("coords", "Announce coordinates in chat", c.getBoolean("events.reveal-coordinates", true)));
        in.add(check("name", "Show the Manhunt holder's name", c.getBoolean("events.reveal-manhunt-name", true)));
        ActionButton save = btn("Save", 120, (v, a) -> {
            int j = (int) snap(f(v, "jug", 60f), 10f);
            c.set("events.juggernaut-minutes", j >= 360 ? 0 : j);
            c.set("events.manhunt-minutes", (int) snap(f(v, "man", 120f), 5f));
            c.set("events.glowing", b(v, "glow", true));
            c.set("events.reveal-interval-seconds", (int) snap(f(v, "int", 60f), 15f));
            c.set("events.reveal-glow-seconds", (int) snap(f(v, "dur", 10f), 1f));
            c.set("events.reveal-coordinates", b(v, "coords", true));
            c.set("events.reveal-manhunt-name", b(v, "name", true));
            saved(p);
            open(p);
        });
        ActionButton back = btn("Back", 100, (v, a) -> open(p));
        p.showDialog(dialog("Events", "Changes apply to the next event you start.", in, DialogType.confirmation(save, back)));
    }
}
