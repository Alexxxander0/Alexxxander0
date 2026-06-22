package com.alexanderp.deltaevents;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class KothManager implements Listener {
    private final DeltaEvents plugin;
    private File kothFile;
    private FileConfiguration config;

    private boolean active = false;
    private final Map<UUID, Integer> captureTimes = new HashMap<>();

    private BukkitTask countdownTask = null;
    private BukkitTask activeTask = null;
    private BukkitTask schedulerTask = null;

    private int remainingTime = 0; // seconds

    // Scheduler states removed

    // Editor selections
    private final Map<UUID, Location> point1 = new HashMap<>();
    private final Map<UUID, Location> point2 = new HashMap<>();

    // Cached placeholders
    private String cachedCappers = "";
    private long lastCappersCheck = 0;

    private String cachedTimeLeft = "";
    private long lastTimeLeftCheck = 0;

    private String cachedSchedule = "";
    private long lastScheduleCheck = 0;

    public KothManager(DeltaEvents plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        stop();
        if (kothFile == null) {
            kothFile = new File(plugin.getDataFolder(), "Koth.yml");
        }
        if (!kothFile.exists()) {
            plugin.saveResource("Koth.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(kothFile);

        plugin.debug("koth", "KOTH configuration reloaded.");
    }

    public FileConfiguration getConfig() {
        return config;
    }

    private String getWorldName() {
        if (config == null) {
            return plugin.getEventWorldName();
        }
        return config.getString("event-world", plugin.getEventWorldName());
    }

    public boolean isActive() {
        return active;
    }

    public boolean isStarting() {
        return countdownTask != null;
    }

    // Scheduler data logic removed

    // --- Event Lifecycle ---

    public void startCountdown(Player sender) {
        if (active || countdownTask != null) {
            if (sender != null) {
                sender.sendMessage(getMessage("koth.already_running"));
            }
            return;
        }

        String worldName = getWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            if (sender != null) {
                sender.sendMessage(DeltaEvents.color(plugin.msg("koth.world_not_loaded").replace("{world}", worldName)));
            }
            return;
        }

        int countdownSec = config.getInt("countdown-seconds", 5);

        countdownTask = new BukkitRunnable() {
            int timeRemaining = countdownSec;

            @Override
            public void run() {
                if (timeRemaining <= 0) {
                    startEvent();
                    cancel();
                    countdownTask = null;
                    return;
                }

                broadcastMessage("koth.countdown", msg -> msg.replace("{time}", String.valueOf(timeRemaining)));
                broadcastSound("countdown");
                timeRemaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startEvent() {
        active = true;
        captureTimes.clear();
        remainingTime = config.getInt("duration-seconds", 600);

        String worldName = getWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            active = false;
            return;
        }

        // Enable PvP based on configuration (defaults to true)
        world.setPVP(config.getBoolean("pvp", true));

        broadcastMessage("koth.started");
        broadcastSound("start");

        // Active game ticking task
        activeTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (remainingTime <= 0) {
                    endEvent();
                    cancel();
                    activeTask = null;
                    return;
                }

                tickCaptureTimes(world);
                sendActionBarUpdate(world);
                remainingTime--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void tickCaptureTimes(World world) {
        for (Player p : world.getPlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            if (isInRegion(p, world, "arena")) {
                captureTimes.put(p.getUniqueId(), captureTimes.getOrDefault(p.getUniqueId(), 0) + 1);
            }
        }
    }

    private void sendActionBarUpdate(World world) {
        String remainingStr = String.format("%02d:%02d", remainingTime / 60, remainingTime % 60);
        List<Player> cappers = getPlayersInRegion(world, "arena");

        String barMsg;
        if (cappers.isEmpty()) {
            barMsg = DeltaEvents.color(plugin.getLang().getString("koth.action_bar_empty", "&a&lKOTH &8• &cНикой не окупира арената! &8| &7Оставащо време: &f{remaining}")
                    .replace("{remaining}", remainingStr));
        } else {
            String capperNames = cappers.stream().map(Player::getName).collect(Collectors.joining(", "));
            barMsg = DeltaEvents.color(plugin.getLang().getString("koth.action_bar_holding", "&a&lKOTH &8• &aОкупиран от: &f{player} &8| &7Оставащо време: &f{remaining}")
                    .replace("{player}", capperNames)
                    .replace("{remaining}", remainingStr));
        }

        for (Player p : world.getPlayers()) {
            p.sendActionBar(barMsg);
        }
    }

    private void endEvent() {
        World world = Bukkit.getWorld(getWorldName());
        Player winner = null;
        int maxTime = -1;

        if (world != null) {
            List<Player> playersInArena = getPlayersInRegion(world, "arena");

            // Look for winner among the players currently inside the arena region
            for (Player p : playersInArena) {
                int timeOnHill = captureTimes.getOrDefault(p.getUniqueId(), 0);
                if (timeOnHill > maxTime) {
                    maxTime = timeOnHill;
                    winner = p;
                }
            }

            // Fallback: If no players are standing inside the arena at the end,
            // award the player with the highest overall capture time who is online in the event world.
            if (winner == null) {
                for (Player p : world.getPlayers()) {
                    int timeOnHill = captureTimes.getOrDefault(p.getUniqueId(), 0);
                    if (timeOnHill > 0 && timeOnHill > maxTime) {
                        maxTime = timeOnHill;
                        winner = p;
                    }
                }
            }
        }

        if (winner != null && maxTime > 0) {
            final Player finalWinner = winner;
            final int finalMaxTime = maxTime;

            broadcastMessage("koth.winner", msg -> msg.replace("{player}", finalWinner.getName()).replace("{time}", formatDuration(finalMaxTime)));

            String titleStr = plugin.getLang().getString("koth.winner_title", "&#4CFF7A&lПОБЕДИТЕЛ!");
            String subtitleStr = plugin.getLang().getString("koth.winner_subtitle", "&f{player} спечели KOTH с {time} на арената!")
                    .replace("{player}", winner.getName())
                    .replace("{time}", formatDuration(maxTime));

            String coloredTitle = DeltaEvents.color(titleStr);
            String coloredSubtitle = DeltaEvents.color(subtitleStr);

            if (world != null) {
                for (Player p : world.getPlayers()) {
                    p.sendTitle(coloredTitle, coloredSubtitle, 10, 80, 20);
                }
            }

            broadcastSound("win");

            // Execute console rewards
            List<String> rewardCmds = config.getStringList("reward-commands");
            for (String cmd : rewardCmds) {
                String replacedCmd = cmd.replace("{player}", winner.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), replacedCmd);
            }
        } else {
            broadcastMessage("koth.no_winner");
        }

        stop();
    }

    public void stop() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }

        if (activeTask != null) {
            activeTask.cancel();
            activeTask = null;
        }

        boolean wasActive = active;
        active = false;

        String worldName = getWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            if (wasActive) {
                broadcastMessage("koth.stopped");
                broadcastSound("stop");
            }
        }

        captureTimes.clear();

        if (config != null && world != null) {
            world.setPVP(false);
        }
    }

    // --- Helpers ---

    private boolean isInRegion(Player p, World world, String regionPrefix) {
        if (!p.getWorld().equals(world)) return false;

        String key = regionPrefix + "-region";
        if (config.isConfigurationSection(key)) {
            String fromStr = config.getString(key + ".from");
            String toStr = config.getString(key + ".to");
            if (fromStr != null && toStr != null) {
                try {
                    String[] fromParts = fromStr.split(",");
                    String[] toParts = toStr.split(",");
                    if (fromParts.length >= 3 && toParts.length >= 3) {
                        double x1 = Double.parseDouble(fromParts[0].trim());
                        double y1 = Double.parseDouble(fromParts[1].trim());
                        double z1 = Double.parseDouble(fromParts[2].trim());

                        double x2 = Double.parseDouble(toParts[0].trim());
                        double y2 = Double.parseDouble(toParts[1].trim());
                        double z2 = Double.parseDouble(toParts[2].trim());

                        double minX = Math.min(x1, x2);
                        double maxX = Math.max(x1, x2) + 1.0;
                        double minY = Math.min(y1, y2);
                        double maxY = Math.max(y1, y2) + 1.5;

                        double minZ = Math.min(z1, z2);
                        double maxZ = Math.max(z1, z2) + 1.0;

                        double px = p.getLocation().getX();
                        double py = p.getLocation().getY();
                        double pz = p.getLocation().getZ();

                        return px >= minX && px <= maxX &&
                               py >= minY && py <= maxY &&
                               pz >= minZ && pz <= maxZ;
                    }
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Failed to parse " + key + " in Koth.yml: " + fromStr + " -> " + toStr);
                }
            }
        }
        return false;
    }

    private List<Player> getPlayersInRegion(World world, String regionPrefix) {
        List<Player> inRegion = new ArrayList<>();
        for (Player p : world.getPlayers()) {
            if (isInRegion(p, world, regionPrefix)) {
                inRegion.add(p);
            }
        }
        return inRegion;
    }

    public Location getSpawnLocation(World world) {
        double x = config.getDouble("spawn-location.x", 0.5);
        double y = config.getDouble("spawn-location.y", 64.0);
        double z = config.getDouble("spawn-location.z", 0.5);
        float yaw = (float) config.getDouble("spawn-location.yaw", 0.0);
        float pitch = (float) config.getDouble("spawn-location.pitch", 0.0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    // --- Message Broadcasting ---

    public String getMessage(String path) {
        return DeltaEvents.color(plugin.msg(path));
    }

    public void broadcastMessage(String path) {
        String worldName = getWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        String msg = plugin.msg(path);
        if (msg == null || msg.isEmpty() || msg.equals(path)) return;

        String colored = DeltaEvents.color(msg);
        for (Player p : world.getPlayers()) {
            p.sendMessage(colored);
        }
    }

    public void broadcastMessage(String path, java.util.function.Function<String, String> replacer) {
        String worldName = getWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        String msg = plugin.getLang().getString(path);
        if (msg == null || msg.isEmpty()) return;

        String replaced = replacer.apply(msg);
        String prefix = plugin.getLang().getString("prefix", "");
        String kothPrefix = plugin.getLang().getString("koth.prefix", "");

        replaced = replaced.replace("{prefix}", prefix).replace("{koth_prefix}", kothPrefix);
        String colored = DeltaEvents.color(replaced);
        for (Player p : world.getPlayers()) {
            p.sendMessage(colored);
        }
    }

    public void broadcastSound(String aliasKey) {
        String worldName = getWorldName();
        String soundAlias = config.getString("sounds." + aliasKey, aliasKey);
        plugin.broadcastSoundToWorld(worldName, soundAlias, 1.0f, 1.0f);
    }

    // --- Editor Wands Creation & Event Handling ---

    public ItemStack createEditorItem(String region) {
        String path = "editor-item." + region;
        String defaultMat = region.equals("spawn") ? "BLAZE_ROD" : "GOLDEN_AXE";
        String defaultName = region.equals("spawn") ? "&#4AA3FF&lKOTH Spawn Location Editor" : "&#FFC107&lKOTH Arena Region Editor";
        String matName = config.getString(path + ".material", defaultMat);
        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = Material.matchMaterial(defaultMat);
        if (mat == null) mat = Material.GOLDEN_AXE;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(DeltaEvents.color(config.getString(path + ".name", defaultName)));

            List<String> lore = config.getStringList(path + ".lore");
            if (lore.isEmpty()) {
                lore = new ArrayList<>();
                if (region.equals("spawn")) {
                    lore.add("&7Кликни върху блок за задаване на спаун");
                } else {
                    lore.add("&7Ляв клик: Точка 1");
                    lore.add("&7Десен клик: Точка 2");
                }
            }
            List<String> coloredLore = new ArrayList<>();
            for (String l : lore) {
                coloredLore.add(DeltaEvents.color(l));
            }
            meta.setLore(coloredLore);

            NamespacedKey key = new NamespacedKey(plugin, "koth_editor");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, region);

            if (config.contains(path + ".nbt-data")) {
                meta.setCustomModelData(config.getInt(path + ".nbt-data"));
            }

            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);

            item.setItemMeta(meta);
        }
        return item;
    }

    public String getKothEditorType(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        if (!item.hasItemMeta()) return null;
        NamespacedKey key = new NamespacedKey(plugin, "koth_editor");
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        String regionType = getKothEditorType(item);
        if (regionType == null) return;

        event.setCancelled(true);

        // Check permission when using the editor wand
        if (!DeltaEvents.hasAnyPermission(player, "DeltaEvents.koth.admin", "DeltaEvents.admin")) {
            player.sendMessage(getMessage("koth.no_permission"));
            return;
        }

        if (event.getClickedBlock() == null) return;
        Location loc = event.getClickedBlock().getLocation();

        if (regionType.equals("spawn")) {
            // Set spawn location
            Location spawnLoc = loc.clone().add(0.5, 1.0, 0.5);
            spawnLoc.setYaw(player.getLocation().getYaw());
            spawnLoc.setPitch(player.getLocation().getPitch());

            config.set("spawn-location.x", spawnLoc.getX());
            config.set("spawn-location.y", spawnLoc.getY());
            config.set("spawn-location.z", spawnLoc.getZ());
            config.set("spawn-location.yaw", (double) spawnLoc.getYaw());
            config.set("spawn-location.pitch", (double) spawnLoc.getPitch());

            try {
                config.save(kothFile);
                player.sendMessage(DeltaEvents.color(plugin.msg("koth.editor_saved").replace("{region}", "spawn")));
                reload();
            } catch (Exception e) {
                player.sendMessage(DeltaEvents.color("&c[KOTH] Failed to save spawn location: " + e.getMessage()));
            }
        } else if (regionType.equals("arena")) {
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                point1.put(player.getUniqueId(), loc);
                player.sendMessage(DeltaEvents.color(plugin.msg("koth.editor_point1_set")
                        .replace("{x}", String.valueOf(loc.getBlockX()))
                        .replace("{y}", String.valueOf(loc.getBlockY()))
                        .replace("{z}", String.valueOf(loc.getBlockZ()))
                        .replace("{region}", regionType)));

                checkAndSaveRegion(player, regionType);
            } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                point2.put(player.getUniqueId(), loc);
                player.sendMessage(DeltaEvents.color(plugin.msg("koth.editor_point2_set")
                        .replace("{x}", String.valueOf(loc.getBlockX()))
                        .replace("{y}", String.valueOf(loc.getBlockY()))
                        .replace("{z}", String.valueOf(loc.getBlockZ()))
                        .replace("{region}", regionType)));

                checkAndSaveRegion(player, regionType);
            }
        }
    }

    private void checkAndSaveRegion(Player player, String regionType) {
        Location loc1 = point1.get(player.getUniqueId());
        Location loc2 = point2.get(player.getUniqueId());
        if (loc1 == null || loc2 == null) return;

        String fromStr = String.format("%d,%d,%d", loc1.getBlockX(), loc1.getBlockY(), loc1.getBlockZ());
        String toStr = String.format("%d,%d,%d", loc2.getBlockX(), loc2.getBlockY(), loc2.getBlockZ());

        config.set(regionType + "-region.from", fromStr);
        config.set(regionType + "-region.to", toStr);

        try {
            config.save(kothFile);
            player.sendMessage(DeltaEvents.color(plugin.msg("koth.editor_saved").replace("{region}", regionType)));
            reload();
        } catch (Exception e) {
            player.sendMessage(DeltaEvents.color("&c[KOTH] Failed to save region config: " + e.getMessage()));
        }

        point1.remove(player.getUniqueId());
        point2.remove(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        point1.remove(uuid);
        point2.remove(uuid);
    }

    // --- Placeholders Logic ---

    private int getPlaceholderInterval(String placeholderName, int defaultValue) {
        if (config == null) return defaultValue;
        String keyWithPct = "%" + placeholderName + "%";
        if (config.contains("placeholders." + keyWithPct)) {
            return config.getInt("placeholders." + keyWithPct);
        }
        if (config.contains("placeholders." + placeholderName)) {
            return config.getInt("placeholders." + placeholderName);
        }
        return defaultValue;
    }

    public String getCurrentCappersString() {
        int interval = getPlaceholderInterval("deltaevents_koth_cappers", 1);
        long now = System.currentTimeMillis();
        if (interval > 0 && now - lastCappersCheck < interval * 1000L) {
            return cachedCappers;
        }

        World world = Bukkit.getWorld(getWorldName());
        if (world == null || !active) {
            cachedCappers = "";
        } else {
            List<Player> players = getPlayersInRegion(world, "arena");
            if (players.isEmpty()) {
                cachedCappers = "";
            } else {
                cachedCappers = players.stream().map(Player::getName).collect(Collectors.joining(", "));
            }
        }
        lastCappersCheck = now;
        return cachedCappers;
    }

    public String getRemainingTimeString() {
        int interval = getPlaceholderInterval("deltaevents_koth_time_left", 1);
        long now = System.currentTimeMillis();
        if (interval > 0 && now - lastTimeLeftCheck < interval * 1000L) {
            return cachedTimeLeft;
        }

        if (!active) {
            cachedTimeLeft = "00:00";
        } else {
            cachedTimeLeft = String.format("%02d:%02d", remainingTime / 60, remainingTime % 60);
        }
        lastTimeLeftCheck = now;
        return cachedTimeLeft;
    }

    public String getScheduleString() {
        int interval = getPlaceholderInterval("deltaevents_koth_schedule", 30);
        long now = System.currentTimeMillis();
        if (interval > 0 && now - lastScheduleCheck < interval * 1000L) {
            return cachedSchedule;
        }

        if (config == null) {
            cachedSchedule = "";
        } else if (config.isList("scheduled-placeholder.days")) {
            cachedSchedule = config.getStringList("scheduled-placeholder.days").stream()
                    .collect(Collectors.joining(", "));
        } else {
            cachedSchedule = config.getString("scheduled-placeholder.days", "");
        }
        lastScheduleCheck = now;
        return cachedSchedule;
    }

    private String formatDuration(int totalSeconds) {
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        if (minutes > 0) {
            return minutes + "m, " + seconds + "s";
        } else {
            return seconds + "s";
        }
    }
}
