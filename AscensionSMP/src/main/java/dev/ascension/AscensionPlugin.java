package dev.ascension;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.stream.Collectors;

public class AscensionPlugin extends JavaPlugin implements Listener, TabExecutor {

    private NamespacedKey keyElement, keyLevel, keyCore, keyShard;
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Random random = new Random();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        keyElement = new NamespacedKey(this, "element");
        keyLevel = new NamespacedKey(this, "level");
        keyCore = new NamespacedKey(this, "core");
        keyShard = new NamespacedKey(this, "shard");

        getServer().getPluginManager().registerEvents(this, this);
        PluginCommand cmd = getCommand("element");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }

        // Passive effects, refreshed every 2 seconds
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) applyPassives(p);
        }, 20L, 40L);

        // Action bar HUD
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) sendHud(p);
        }, 20L, 10L);

        getLogger().info("Ascension SMP enabled.");
    }

    // ---------------------------------------------------------------- data

    private Element getElement(Player p) {
        String s = p.getPersistentDataContainer().get(keyElement, PersistentDataType.STRING);
        return s == null ? null : Element.parse(s);
    }

    private void setElement(Player p, Element e) {
        p.getPersistentDataContainer().set(keyElement, PersistentDataType.STRING, e.name());
        clearPassives(p);
        giveCore(p);
    }

    private int getLevel(Player p) {
        Integer l = p.getPersistentDataContainer().get(keyLevel, PersistentDataType.INTEGER);
        return l == null ? 1 : l;
    }

    private void setLevel(Player p, int level) {
        int max = getConfig().getInt("max-level", 5);
        level = Math.max(1, Math.min(max, level));
        p.getPersistentDataContainer().set(keyLevel, PersistentDataType.INTEGER, level);
    }

    private Element randomElement(Element exclude) {
        List<Element> pool = new ArrayList<>(Arrays.asList(Element.values()));
        if (exclude != null) pool.remove(exclude);
        return pool.get(random.nextInt(pool.size()));
    }

    // ---------------------------------------------------------------- items

    private boolean isCore(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(keyCore, PersistentDataType.BYTE);
    }

    private boolean isShard(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(keyShard, PersistentDataType.BYTE);
    }

    private ItemStack makeCore(Element e) {
        ItemStack it = new ItemStack(e.icon());
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(e.color() + "" + ChatColor.BOLD + e.display() + " Core");
        m.setLore(List.of(
                ChatColor.GRAY + "Passive: " + ChatColor.WHITE + e.passive(),
                ChatColor.GRAY + "Right-click: " + e.color() + e.abilityName(),
                ChatColor.DARK_GRAY + e.abilityDesc(),
                "",
                ChatColor.DARK_GRAY + "Bound to you. Cannot be dropped."));
        m.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        m.getPersistentDataContainer().set(keyCore, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private ItemStack makeShard() {
        ItemStack it = new ItemStack(Material.ECHO_SHARD);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Rift Shard");
        m.setLore(List.of(
                ChatColor.GRAY + "Right-click to reroll your element.",
                ChatColor.DARK_GRAY + "Dropped by slain players."));
        m.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        m.getPersistentDataContainer().set(keyShard, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private void giveCore(Player p) {
        Element e = getElement(p);
        if (e == null) return;
        for (ItemStack it : p.getInventory().getContents()) {
            if (isCore(it)) p.getInventory().remove(it);
        }
        Map<Integer, ItemStack> leftover = p.getInventory().addItem(makeCore(e));
        leftover.values().forEach(i -> p.getWorld().dropItem(p.getLocation(), i));
    }

    // ---------------------------------------------------------------- passives

    private void applyPassives(Player p) {
        Element e = getElement(p);
        if (e == null || p.getGameMode() == GameMode.SPECTATOR) return;
        int lvl = getLevel(p);
        switch (e) {
            case EMBER -> effect(p, PotionEffectType.FIRE_RESISTANCE, 100, 0);
            case TIDE -> {
                effect(p, PotionEffectType.WATER_BREATHING, 100, 0);
                if (p.isInWater()) effect(p, PotionEffectType.DOLPHINS_GRACE, 100, 0);
            }
            case GALE -> effect(p, PotionEffectType.SPEED, 100, lvl >= 4 ? 1 : 0);
            case TERRA -> effect(p, PotionEffectType.HEALTH_BOOST, 100, (lvl - 1) / 2);
            case VOID -> effect(p, PotionEffectType.NIGHT_VISION, 260, 0);
        }
    }

    private void clearPassives(Player p) {
        for (PotionEffectType t : List.of(PotionEffectType.FIRE_RESISTANCE, PotionEffectType.WATER_BREATHING,
                PotionEffectType.DOLPHINS_GRACE, PotionEffectType.SPEED, PotionEffectType.HEALTH_BOOST,
                PotionEffectType.NIGHT_VISION)) {
            p.removePotionEffect(t);
        }
    }

    private void effect(Player p, PotionEffectType type, int ticks, int amp) {
        p.addPotionEffect(new PotionEffect(type, ticks, amp, true, false, true));
    }

    // ---------------------------------------------------------------- cooldown + HUD

    private long cooldownMillis(int level) {
        int base = getConfig().getInt("base-cooldown", 32);
        int red = getConfig().getInt("cooldown-reduction-per-level", 4);
        return Math.max(5, base - red * (level - 1)) * 1000L;
    }

    private void sendHud(Player p) {
        Element e = getElement(p);
        if (e == null) return;
        long left = cooldowns.getOrDefault(p.getUniqueId(), 0L) - System.currentTimeMillis();
        String status = left > 0
                ? ChatColor.RED + String.format("%.1fs", left / 1000.0)
                : ChatColor.GREEN + "READY";
        p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                new net.md_5.bungee.api.chat.TextComponent(
                        e.color() + "" + ChatColor.BOLD + e.display()
                                + ChatColor.GRAY + " | " + ChatColor.GOLD + "Lv " + getLevel(p)
                                + ChatColor.GRAY + " | " + e.abilityName() + ": " + status));
    }

    // ---------------------------------------------------------------- events

    @EventHandler
    public void onJoin(PlayerJoinEvent ev) {
        Player p = ev.getPlayer();
        if (getElement(p) == null) {
            Element e = randomElement(null);
            setElement(p, e);
            setLevel(p, 1);
            p.sendMessage(ChatColor.GOLD + "You have awakened as " + e.colored() + ChatColor.GOLD + "!");
            p.sendMessage(ChatColor.GRAY + "Right-click your Core to use " + e.abilityName() + ". Kill players to ascend.");
            p.playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.4f);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent ev) {
        if (ev.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (ev.getAction() != Action.RIGHT_CLICK_AIR && ev.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = ev.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();

        if (isShard(hand)) {
            ev.setCancelled(true);
            Element old = getElement(p);
            Element fresh = randomElement(old);
            hand.setAmount(hand.getAmount() - 1);
            setElement(p, fresh);
            p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 80, 0.5, 1, 0.5, 0.5);
            p.playSound(p.getLocation(), Sound.BLOCK_END_PORTAL_SPAWN, 0.6f, 1.5f);
            Bukkit.broadcastMessage(ChatColor.LIGHT_PURPLE + p.getName() + ChatColor.GRAY
                    + " shattered a Rift Shard and became " + fresh.colored() + ChatColor.GRAY + "!");
            return;
        }

        if (!isCore(hand)) return;
        ev.setCancelled(true);
        Element e = getElement(p);
        if (e == null) return;

        long now = System.currentTimeMillis();
        long ready = cooldowns.getOrDefault(p.getUniqueId(), 0L);
        if (now < ready) {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.6f);
            return;
        }
        int lvl = getLevel(p);
        cast(p, e, lvl);
        cooldowns.put(p.getUniqueId(), now + cooldownMillis(lvl));
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent ev) {
        if (isCore(ev.getItemDrop().getItemStack())) ev.setCancelled(true);
    }

    @EventHandler
    public void onFall(EntityDamageEvent ev) {
        if (ev.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (ev.getEntity() instanceof Player p && getElement(p) == Element.GALE) ev.setCancelled(true);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent ev) {
        Player victim = ev.getEntity();
        ev.getDrops().removeIf(this::isCore);
        cooldowns.remove(victim.getUniqueId());

        int vOld = getLevel(victim);
        setLevel(victim, vOld - 1);
        if (getLevel(victim) < vOld) {
            victim.sendMessage(ChatColor.RED + "You fell to level " + getLevel(victim) + ".");
        }

        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            int kOld = getLevel(killer);
            setLevel(killer, kOld + 1);
            if (getLevel(killer) > kOld) {
                killer.sendMessage(ChatColor.GOLD + "You ascended to level " + getLevel(killer) + "!");
                killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            }
            if (random.nextDouble() < getConfig().getDouble("shard-drop-chance", 0.15)) {
                ev.getDrops().add(makeShard());
                killer.sendMessage(ChatColor.LIGHT_PURPLE + "A Rift Shard dropped!");
            }
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent ev) {
        Player p = ev.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> giveCore(p), 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent ev) {
        cooldowns.remove(ev.getPlayer().getUniqueId());
    }

    // ---------------------------------------------------------------- abilities

    private void cast(Player p, Element e, int lvl) {
        World w = p.getWorld();
        Location loc = p.getLocation();
        switch (e) {
            case EMBER -> {
                double r = 4 + lvl;
                w.spawnParticle(Particle.FLAME, loc.clone().add(0, 1, 0), 150, r / 2, 0.6, r / 2, 0.08);
                w.playSound(loc, Sound.ENTITY_BLAZE_SHOOT, 1.2f, 0.7f);
                for (LivingEntity t : nearby(p, r)) {
                    t.setFireTicks(60 + lvl * 20);
                    t.damage(2 + lvl, p);
                    t.setVelocity(t.getLocation().toVector().subtract(loc.toVector()).setY(0.4).normalize().multiply(1.0));
                }
            }
            case TIDE -> {
                double r = 5 + lvl;
                w.spawnParticle(Particle.SPLASH, loc.clone().add(0, 1, 0), 250, r / 2, 0.8, r / 2, 0.2);
                w.playSound(loc, Sound.ENTITY_PLAYER_SPLASH_HIGH_SPEED, 1.2f, 0.8f);
                for (LivingEntity t : nearby(p, r)) {
                    t.setVelocity(t.getLocation().toVector().subtract(loc.toVector()).setY(0.35).normalize().multiply(1.3));
                    t.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80 + lvl * 10, 1));
                }
                effect(p, PotionEffectType.REGENERATION, 100, 1);
            }
            case GALE -> {
                Vector dir = loc.getDirection().normalize().multiply(1.2 + 0.2 * lvl).setY(0.45);
                p.setVelocity(dir);
                w.spawnParticle(Particle.CLOUD, loc, 40, 0.3, 0.1, 0.3, 0.05);
                w.playSound(loc, Sound.ENTITY_BREEZE_JUMP, 1.2f, 1f);
            }
            case TERRA -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, (5 + lvl) * 20, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, (5 + lvl) * 20, 1));
                w.spawnParticle(Particle.BLOCK, loc.clone().add(0, 1, 0), 80, 0.5, 0.8, 0.5,
                        Material.STONE.createBlockData());
                w.playSound(loc, Sound.BLOCK_STONE_PLACE, 1.4f, 0.5f);
            }
            case VOID -> {
                double range = 6 + lvl * 2;
                Location eye = p.getEyeLocation();
                Vector dir = eye.getDirection().normalize();
                RayTraceResult hit = p.rayTraceBlocks(range);
                double dist = range;
                if (hit != null) {
                    dist = Math.max(0, hit.getHitPosition().distance(eye.toVector()) - 1.0);
                }
                w.spawnParticle(Particle.PORTAL, loc.clone().add(0, 1, 0), 60, 0.3, 0.6, 0.3, 0.4);
                Location dest = loc.clone().add(dir.multiply(dist));
                p.teleport(dest);
                w.spawnParticle(Particle.REVERSE_PORTAL, dest.clone().add(0, 1, 0), 60, 0.3, 0.6, 0.3, 0.2);
                w.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
            }
        }
    }

    private List<LivingEntity> nearby(Player p, double r) {
        return p.getNearbyEntities(r, r, r).stream()
                .filter(en -> en instanceof LivingEntity && !en.equals(p))
                .filter(en -> !(en instanceof Player pl) || pl.getGameMode() == GameMode.SURVIVAL)
                .map(en -> (LivingEntity) en)
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------- commands

    @Override
    public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        if (a.length == 0 || a[0].equalsIgnoreCase("info")) {
            if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
            Element e = getElement(p);
            if (e == null) { p.sendMessage(ChatColor.RED + "No element yet."); return true; }
            p.sendMessage(ChatColor.GOLD + "--- Ascension ---");
            p.sendMessage(ChatColor.GRAY + "Element: " + e.colored() + ChatColor.GRAY + "  Level: " + ChatColor.GOLD + getLevel(p));
            p.sendMessage(ChatColor.GRAY + "Passive: " + ChatColor.WHITE + e.passive());
            p.sendMessage(ChatColor.GRAY + "Ability: " + e.color() + e.abilityName() + ChatColor.GRAY + " - " + e.abilityDesc());
            p.sendMessage(ChatColor.GRAY + "Cooldown: " + ChatColor.WHITE + cooldownMillis(getLevel(p)) / 1000 + "s");
            return true;
        }

        if (a[0].equalsIgnoreCase("core")) {
            if (s instanceof Player p) { giveCore(p); p.sendMessage(ChatColor.GREEN + "Core restored."); }
            return true;
        }

        if (!s.hasPermission("ascension.admin")) {
            s.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }

        if (a[0].equalsIgnoreCase("set") && a.length >= 3) {
            Player t = Bukkit.getPlayer(a[1]);
            Element e = Element.parse(a[2]);
            if (t == null || e == null) { s.sendMessage(ChatColor.RED + "Unknown player or element."); return true; }
            setElement(t, e);
            s.sendMessage(ChatColor.GREEN + t.getName() + " is now " + e.display() + ".");
            return true;
        }

        if (a[0].equalsIgnoreCase("level") && a.length >= 3) {
            Player t = Bukkit.getPlayer(a[1]);
            if (t == null) { s.sendMessage(ChatColor.RED + "Unknown player."); return true; }
            try {
                setLevel(t, Integer.parseInt(a[2]));
                s.sendMessage(ChatColor.GREEN + t.getName() + " is now level " + getLevel(t) + ".");
            } catch (NumberFormatException ex) {
                s.sendMessage(ChatColor.RED + "Level must be a number.");
            }
            return true;
        }

        if (a[0].equalsIgnoreCase("shard") && s instanceof Player p) {
            p.getInventory().addItem(makeShard());
            return true;
        }

        s.sendMessage(ChatColor.GRAY + "/element [info|core|set <player> <element>|level <player> <n>|shard]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] a) {
        if (a.length == 1) {
            return filter(List.of("info", "core", "set", "level", "shard"), a[0]);
        }
        if (a.length == 2 && (a[0].equalsIgnoreCase("set") || a[0].equalsIgnoreCase("level"))) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), a[1]);
        }
        if (a.length == 3 && a[0].equalsIgnoreCase("set")) {
            return filter(Arrays.stream(Element.values()).map(e -> e.name().toLowerCase()).toList(), a[2]);
        }
        return List.of();
    }

    private List<String> filter(List<String> opts, String prefix) {
        return opts.stream().filter(o -> o.toLowerCase().startsWith(prefix.toLowerCase())).toList();
    }
}
