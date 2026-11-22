package com.redeemed;

import com.redeemed.Gems.Stage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public final class RedeemedGemPlugin extends JavaPlugin implements CommandExecutor, TabCompleter, Listener {
    public Gems gems;
    public TrustStore trust;
    public RedeemedStore redeemed;
    public AbilityListener abilities;
    public MenuListener menus;
    RitualData ritual;
    SetTwo setTwo;
    EventManager events;
    ConfigMenus dialogs;
    private static RedeemedGemPlugin inst;

    static RedeemedGemPlugin get() { return inst; }

    int abilitySet() { return getConfig().getInt("ability-set", 2) == 1 ? 1 : 2; }

    /** Rebuilds every gem in online players' inventories so tooltips match the active ability set. */
    void refreshGems() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Stage st = gems.stageInInventory(p);
            if (st != null) gems.setStage(p, st);
        }
    }

    /** Gives the Awakened Redeemed Gem, registers the player and clears their Bliss gems. */
    void grantGem(Player t) {
        redeemed.add(t.getUniqueId());
        gems.removeBlissGems(t);
        for (ItemStack left : t.getInventory().addItem(gems.create(Stage.AWAKENED, null)).values())
            t.getWorld().dropItem(t.getLocation(), left);
        t.playSound(t.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.6f);
    }

    @Override
    public void onEnable() {
        inst = this;
        saveDefaultConfig();
        // Fill in any new options (e.g. slam.charges, passive.*) on configs made by an older version, keeping existing values.
        getConfig().options().copyDefaults(true);
        saveConfig();
        gems = new Gems(this);
        trust = new TrustStore(this);
        redeemed = new RedeemedStore(this);
        abilities = new AbilityListener(this);
        menus = new MenuListener(this);
        ritual = new RitualData(this);
        ritual.load();
        setTwo = new SetTwo(this);
        events = new EventManager(this);
        dialogs = new ConfigMenus(this);
        Bukkit.getPluginManager().registerEvents(setTwo, this);
        Bukkit.getPluginManager().registerEvents(events, this);
        Bukkit.getPluginManager().registerEvents(new Soulbound(this), this);
        Bukkit.getScheduler().runTaskTimer(this, () -> { events.tick(); setTwo.tickActionBar(); }, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, () -> events.fxTick(), 20L, 3L);
        Bukkit.getPluginManager().registerEvents(abilities, this);
        Bukkit.getPluginManager().registerEvents(menus, this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getCommand("rg").setExecutor(this);
        getCommand("rg").setTabCompleter(this);
        // Bliss re-gives gems on join/death/etc, so keep stripping them from Redeemed Gem players.
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (UUID id : redeemed.all()) {
                Player p = Bukkit.getPlayer(id);
                if (p != null && p.isOnline()) gems.removeBlissGems(p);
            }
        }, 40L, 20L);
    }

    @Override
    public void onDisable() {
        if (abilities != null) abilities.shutdown();
        if (setTwo != null) setTwo.shutdown();
        if (events != null) events.shutdown();
        if (ritual != null) ritual.save();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (redeemed.has(p.getUniqueId())) Bukkit.getScheduler().runTaskLater(this, () -> gems.removeBlissGems(p), 40L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        if (redeemed.has(p.getUniqueId())) Bukkit.getScheduler().runTaskLater(this, () -> gems.removeBlissGems(p), 20L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent e) {
        if (e.getEntity() instanceof Player p && redeemed.has(p.getUniqueId()) && gems.isGemLike(e.getItem().getItemStack())) e.setCancelled(true);
    }

    // ------------------------------------------------------------ /trust mirroring
    /** BlissPlugin owns /trust. We watch it and keep our own copy so Redeemed Gem abilities skip trusted players. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTrustCommand(PlayerCommandPreprocessEvent e) {
        String[] a = e.getMessage().trim().split("\\s+");
        String label = a[0].toLowerCase(Locale.ROOT);
        if (label.startsWith("/")) label = label.substring(1);
        if (label.contains(":")) label = label.substring(label.indexOf(':') + 1);
        if (!label.equals("trust") || a.length < 2) return;
        UUID owner = e.getPlayer().getUniqueId();
        String sub = a[1].toLowerCase(Locale.ROOT);
        if (sub.equals("clear")) { trust.clear(owner); return; }
        if (a.length < 3 || (!sub.equals("add") && !sub.equals("remove"))) return;
        OfflinePlayer o = Bukkit.getPlayerExact(a[2]);
        if (o == null) o = Bukkit.getOfflinePlayerIfCached(a[2]);
        if (o == null) return;
        if (sub.equals("add")) trust.add(owner, o.getUniqueId()); else trust.remove(owner, o.getUniqueId());
    }

    // ------------------------------------------------------------ Commands
    private static Component msg(String s) { return Component.text(s, NamedTextColor.GRAY); }

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 0) { s.sendMessage(msg("/rg <config|give|trust|untrust|cooldown|event|wisp|scroll|ritual|divinity|progression|disguise|start|stop|trial|set|debug>")); return true; }
        String sub = a[0].toLowerCase(Locale.ROOT);

        if (sub.equals("trust") || sub.equals("untrust")) { return trustCmd(s, a); }
        if (sub.equals("ritual") && a.length >= 2 && a[1].equalsIgnoreCase("start")) {
            if (s instanceof Player p) events.startRitual(p); else s.sendMessage(msg("Players only."));
            return true;
        }
        if (!s.hasPermission("redeemedgem.admin")) { s.sendMessage(Component.text("No permission.", NamedTextColor.RED)); return true; }

        switch (sub) {
            case "config" -> {
                if (!(s instanceof Player p)) { s.sendMessage(msg("Players only.")); return true; }
                dialogs.open(p);
            }
            case "start" -> {
                Player t = target(s, a, 1);
                if (t == null) return true;
                redeemed.add(t.getUniqueId());
                for (String cmd : getConfig().getStringList("bliss-gem-detect.start-commands"))
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", t.getName()));
                int removed = gems.removeBlissGems(t);
                for (ItemStack left : t.getInventory().addItem(gems.create(Stage.DORMANT, null)).values())
                    t.getWorld().dropItem(t.getLocation(), left);
                t.playSound(t.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 0.6f);
                s.sendMessage(msg(t.getName() + " now has the Dormant Redeemed Gem (removed " + removed + " Bliss gem item(s))."));
            }
            case "trial" -> {
                if (a.length < 4 || !a[2].equalsIgnoreCase("start")) { s.sendMessage(msg("/rg trial <1-3> start <player>")); return true; }
                int n;
                try { n = Integer.parseInt(a[1]); } catch (NumberFormatException ex) { n = 0; }
                if (n < 1 || n > 3) { s.sendMessage(msg("Trial must be 1, 2 or 3.")); return true; }
                Player t = target(s, a, 3);
                if (t == null) return true;
                trial(s, t, n);
            }
            case "stop" -> {
                Player t = target(s, a, 1);
                if (t == null) return true;
                redeemed.remove(t.getUniqueId());
                for (int i = 0; i < t.getInventory().getSize(); i++) if (gems.isRedeemed(t.getInventory().getItem(i))) t.getInventory().setItem(i, null);
                s.sendMessage(msg(t.getName() + " is no longer a Redeemed Gem holder. Give them a Bliss gem back with /bliss gemgem " + t.getName() + "."));
            }
            case "debug" -> {
                if (!(s instanceof Player p)) { s.sendMessage(msg("Players only.")); return true; }
                for (String line : gems.describe(p.getInventory().getItemInMainHand())) s.sendMessage(msg(line));
            }
            case "set" -> {
                Player t = target(s, a, 1);
                if (t == null || a.length < 3) { s.sendMessage(msg("/rg set <player> <dormant|unawakened|awakening|awakened>")); return true; }
                try {
                    Stage st = Stage.valueOf(a[2].toUpperCase(Locale.ROOT));
                    gems.setStage(t, st);
                    s.sendMessage(msg("Set " + t.getName() + "'s Redeemed Gem to " + st.label + "."));
                } catch (IllegalArgumentException ex) { s.sendMessage(msg("Unknown stage.")); }
            }
            case "give" -> {
                Player t = target(s, a, 1);
                if (t == null) return true;
                String what = a.length >= 3 ? a[2].toLowerCase(Locale.ROOT) : "gem";
                switch (what) {
                    case "scroll" -> t.getInventory().addItem(RItems.scroll());
                    case "compass" -> t.getInventory().addItem(RItems.compass());
                    case "fragment" -> {
                        int n = a.length >= 4 ? parseInt(a[3]) : 1;
                        if (n < 1 || n > 3) { s.sendMessage(msg("Fragment must be 1-3.")); return true; }
                        t.getInventory().addItem(RItems.fragment(n));
                    }
                    default -> grantGem(t);
                }
                s.sendMessage(msg("Gave " + what + " to " + t.getName() + "."));
            }
            case "cooldown" -> {
                Player t = a.length >= 2 ? Bukkit.getPlayerExact(a[1]) : (s instanceof Player p ? p : null);
                if (t == null) { s.sendMessage(msg("/rg cooldown [player]")); return true; }
                abilities.resetCooldowns(t.getUniqueId());
                setTwo.resetCooldowns(t.getUniqueId());
                s.sendMessage(msg("Cooldowns reset for " + t.getName() + "."));
            }
            case "disguise" -> {
                if (s instanceof Player p) menus.openDisguise(p); else s.sendMessage(msg("Players only."));
            }
            case "scroll" -> {
                if (a.length < 2) { s.sendMessage(msg("/rg scroll <player|random>")); return true; }
                if (a[1].equalsIgnoreCase("random")) {
                    ritual.scrollGiven = false;
                    events.autoGiveScroll();
                    s.sendMessage(msg(ritual.scrollGiven ? "Scroll given to a random player." : "No eligible players online."));
                } else {
                    Player t = Bukkit.getPlayerExact(a[1]);
                    if (t == null) { s.sendMessage(msg("Player not online.")); return true; }
                    events.giveScroll(t);
                    ritual.scrollGiven = true;
                    ritual.save();
                }
            }
            case "ritual" -> {
                if (a.length >= 2 && a[1].equalsIgnoreCase("reset")) { ritual.resetRitual(); s.sendMessage(msg("Ritual reset.")); }
                else s.sendMessage(msg("/rg ritual <reset>"));
            }
            case "divinity" -> {
                if (a.length >= 2 && a[1].equalsIgnoreCase("here") && s instanceof Player p) {
                    ritual.divinity = p.getLocation().clone();
                    ritual.ritualActive = true;
                    ritual.save();
                    s.sendMessage(msg("Divinity moved here (ritual marked active)."));
                } else s.sendMessage(msg("/rg divinity here"));
            }
            case "progression" -> {
                if (a.length < 2 || parseInt(a[1]) < 1) { s.sendMessage(msg("/rg progression <number> (current: " + getConfig().getInt("progression") + ")")); return true; }
                getConfig().set("progression", parseInt(a[1]));
                saveConfig();
                s.sendMessage(msg("Progression set to " + a[1] + "."));
            }
            case "wisp" -> {
                if (a.length < 3) { s.sendMessage(msg("/rg wisp <set|clear> <player>")); return true; }
                Player t = Bukkit.getPlayerExact(a[2]);
                if (t == null) { s.sendMessage(msg("Player not online.")); return true; }
                events.setWisp(t.getUniqueId(), a[1].equalsIgnoreCase("set"));
                s.sendMessage(msg((a[1].equalsIgnoreCase("set") ? "Wisp set on " : "Wisp cleared from ") + t.getName() + "."));
            }
            case "event" -> event(s, a);
            default -> s.sendMessage(msg("Unknown subcommand. /rg for the list."));
        }
        return true;
    }

    private int parseInt(String x) {
        try { return Integer.parseInt(x); } catch (NumberFormatException e) { return -1; }
    }

    private void event(CommandSender s, String[] a) {
        if (a.length < 3) { s.sendMessage(msg("/rg event <juggernaut|manhunt> ...")); return; }
        String type = a[1].toLowerCase(Locale.ROOT), act = a[2].toLowerCase(Locale.ROOT);
        if (type.equals("juggernaut")) {
            if (act.equals("set") && a.length >= 4) {
                Player t = Bukkit.getPlayerExact(a[3]);
                if (t == null) { s.sendMessage(msg("Player not online.")); return; }
                events.startJuggernaut(t);
            } else if (act.equals("stop")) {
                events.stopJuggernaut(true);
                s.sendMessage(msg("Juggernaut event stopped."));
            } else s.sendMessage(msg("/rg event juggernaut <set <player>|stop>"));
        } else if (type.equals("manhunt")) {
            switch (act) {
                case "start" -> { if (!events.startManhunt()) s.sendMessage(msg("No eligible players online.")); }
                case "stop" -> { events.stopManhunt(); s.sendMessage(msg("Manhunt stopped.")); }
                case "exempt" -> {
                    OfflinePlayer o = a.length >= 4 ? Bukkit.getPlayerExact(a[3]) : null;
                    if (o == null && a.length >= 4) o = Bukkit.getOfflinePlayerIfCached(a[3]);
                    if (o == null) { s.sendMessage(msg("/rg event manhunt exempt <player>")); return; }
                    events.exempt(o.getUniqueId());
                    s.sendMessage(msg(a[3] + " is exempt from the Manhunt."));
                }
                case "status" -> {
                    Player t = a.length >= 5 && a[3].equalsIgnoreCase("give") ? Bukkit.getPlayerExact(a[4]) : null;
                    if (t == null) { s.sendMessage(msg("/rg event manhunt status give <player>")); return; }
                    events.giveStatus(t, false);
                }
                default -> s.sendMessage(msg("/rg event manhunt <start|stop|exempt <player>|status give <player>>"));
            }
        } else s.sendMessage(msg("/rg event <juggernaut|manhunt> ..."));
    }

    private Player target(CommandSender s, String[] a, int idx) {
        if (a.length <= idx) { s.sendMessage(msg("Specify a player.")); return null; }
        Player t = Bukkit.getPlayerExact(a[idx]);
        if (t == null) s.sendMessage(msg("Player not online."));
        return t;
    }

    private void trial(CommandSender s, Player t, int n) {
        Stage cur = gems.stageInInventory(t);
        if (cur == null) { s.sendMessage(msg(t.getName() + " has no Redeemed Gem. Use /rg start first.")); return; }
        if (cur.ordinal() != n - 1) {
            s.sendMessage(msg(t.getName() + "'s gem is " + cur.label + "; trial " + n + " needs " + Stage.values()[n - 1].label + "."));
            return;
        }
        Stage next = Stage.values()[n];
        t.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 20, 5, false, false, false));
        t.playSound(t.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.5f);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!t.isOnline()) return;
            t.removePotionEffect(PotionEffectType.LEVITATION);
            t.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 120, 0, false, false, false));
            gems.setStage(t, next);
            abilities.particleBurst(t.getLocation().add(0, 1, 0));
            t.playSound(t.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 1.5f);
            t.showTitle(net.kyori.adventure.title.Title.title(
                    Component.text(next.label, next.color), Component.text("Redeemed Gem", NamedTextColor.GRAY)));
        }, 20L);
        s.sendMessage(msg("Trial " + n + " started for " + t.getName() + "."));
    }

    private boolean trustCmd(CommandSender s, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage(msg("Players only.")); return true; }
        boolean untrust = a[0].equalsIgnoreCase("untrust");
        if (a.length < 2) { s.sendMessage(msg("/rg trust <player>   /rg untrust <player>   /rg trust list|clear")); return true; }
        String act = a[1].toLowerCase(Locale.ROOT);
        if (!untrust && act.equals("clear")) { trust.clear(p.getUniqueId()); s.sendMessage(msg("Redeemed Gem trust list cleared.")); return true; }
        if (!untrust && act.equals("list")) {
            List<String> names = new ArrayList<>();
            for (UUID u : trust.list(p.getUniqueId())) names.add(String.valueOf(Bukkit.getOfflinePlayer(u).getName()));
            s.sendMessage(msg("Trusted: " + (names.isEmpty() ? "nobody" : String.join(", ", names))));
            return true;
        }
        // /rg trust <player>, /rg untrust <player> (the older "/rg trust add|remove <player>" still works)
        boolean remove = untrust || act.equals("remove");
        String name = (act.equals("add") || act.equals("remove")) ? (a.length >= 3 ? a[2] : null) : a[1];
        if (name == null) { s.sendMessage(msg("Specify a player.")); return true; }
        OfflinePlayer o = Bukkit.getPlayerExact(name);
        if (o == null) o = Bukkit.getOfflinePlayerIfCached(name);
        if (o == null) { s.sendMessage(msg("Unknown player.")); return true; }
        if (remove) trust.remove(p.getUniqueId(), o.getUniqueId()); else trust.add(p.getUniqueId(), o.getUniqueId());
        s.sendMessage(msg(remove ? "Untrusted " + name + ": the gem works on them again." : "Trusted " + name + ": the gem no longer affects them."));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] a) {
        List<String> out = new ArrayList<>();
        List<String> players = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) players.add(p.getName());
        String first = a[0].toLowerCase(Locale.ROOT);
        if (a.length == 1) out.addAll(List.of("config", "give", "trust", "untrust", "cooldown", "event", "wisp", "scroll", "ritual", "divinity", "progression", "disguise", "start", "stop", "trial", "set", "debug"));
        else if (a.length == 2) {
            switch (first) {
                case "trial" -> out.addAll(List.of("1", "2", "3"));
                case "trust" -> { out.addAll(List.of("add", "remove", "list", "clear")); out.addAll(players); }
                case "event" -> out.addAll(List.of("juggernaut", "manhunt"));
                case "wisp" -> out.addAll(List.of("set", "clear"));
                case "ritual" -> out.addAll(List.of("start", "reset"));
                case "scroll" -> { out.add("random"); out.addAll(players); }
                case "divinity" -> out.add("here");
                default -> out.addAll(players);
            }
        } else if (a.length == 3) {
            switch (first) {
                case "trial" -> out.add("start");
                case "set" -> { for (Stage st : Stage.values()) out.add(st.name().toLowerCase(Locale.ROOT)); }
                case "give" -> out.addAll(List.of("gem", "scroll", "fragment", "compass"));
                case "wisp" -> out.addAll(players);
                case "trust" -> out.addAll(players);
                case "event" -> out.addAll(a[1].equalsIgnoreCase("juggernaut") ? List.of("set", "stop") : List.of("start", "stop", "exempt", "status"));
                default -> {}
            }
        } else if (a.length == 4 && first.equals("event")) {
            if (a[2].equalsIgnoreCase("status")) out.add("give"); else out.addAll(players);
        } else if (a.length == 5 && first.equals("event")) out.addAll(players);
        String last = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(last));
        return out;
    }
}
