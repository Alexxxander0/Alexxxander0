package com.alexanderp.deltaevents;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.BlockState;
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
import java.util.*;

public class FiveMManager implements Listener {
    private final DeltaEvents plugin;
    private File fiveMFile;
    private FileConfiguration config;

    private boolean active = false;
    private UUID currentGameId = null;
    private final Set<UUID> activePlayers = new HashSet<>();
    private final Map<Location, BlockState> originalStates = new HashMap<>();

    private BukkitTask countdownTask = null;
    private BukkitTask gameTask = null;

    private int roundNumber = 0;
    private double currentDuration = 5.0;

    // Editor coordinates mapping
    private final Map<UUID, Location> point1 = new HashMap<>();
    private final Map<UUID, Location> point2 = new HashMap<>();

    private static final Map<String, String> COLOR_NAMES = new HashMap<>();
    static {
        COLOR_NAMES.put("RED", "&cЧЕРВЕНО");
        COLOR_NAMES.put("GREEN", "&aЗЕЛЕНО");
        COLOR_NAMES.put("BLUE", "&9СИНЬО");
        COLOR_NAMES.put("YELLOW", "&eЖЪЛТО");
        COLOR_NAMES.put("ORANGE", "&6ОРАНЖЕВО");
        COLOR_NAMES.put("PURPLE", "&5ЛИЛАВО");
        COLOR_NAMES.put("PINK", "&dРОЗОВО");
        COLOR_NAMES.put("LIME", "&aСВЕТЛОЗЕЛЕНО");
        COLOR_NAMES.put("CYAN", "&3ЦИАН");
        COLOR_NAMES.put("MAGENTA", "&dМАДЖЕНТА");
        COLOR_NAMES.put("WHITE", "&fБЯЛО");
        COLOR_NAMES.put("BLACK", "&0ЧЕРНО");
        COLOR_NAMES.put("GRAY", "&7СИВО");
        COLOR_NAMES.put("LIGHT_GRAY", "&7СВЕТЛОСИВО");
        COLOR_NAMES.put("BROWN", "&6КАФЯВО");
        COLOR_NAMES.put("LIGHT_BLUE", "&bСВЕТЛОСИНЬО");
    }

    public FiveMManager(DeltaEvents plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        stop();
        if (fiveMFile == null) {
            fiveMFile = new File(plugin.getDataFolder(), "FiveM.yml");
        }
        if (!fiveMFile.exists()) {
            plugin.saveResource("FiveM.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(fiveMFile);
        plugin.debug("fivem", "FiveM configuration reloaded.");
    }

    public FileConfiguration getConfig() {
        return config;
    }

    private String getWorldName() {
        return plugin.getEventWorldName();
    }

    public boolean isActive() {
        return active;
    }

    public boolean isStarting() {
        return countdownTask != null;
    }

    public void startCountdown(Player sender) {
        if (active || countdownTask != null) {
            if (sender != null) {
                plugin.sendMessage(sender, "fivem.already_running");
            }
            return;
        }

        String worldName = getWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            if (sender != null) {
                sender.sendMessage(DeltaEvents.color(plugin.msg("fivem.world_not_loaded").replace("{world}", worldName)));
            }
            return;
        }

        currentGameId = UUID.randomUUID();
        final UUID gameId = currentGameId;

        int countdownSec = config.getInt("countdown-seconds", 5);

        countdownTask = new BukkitRunnable() {
            int timeRemaining = countdownSec;

            @Override
            public void run() {
                if (currentGameId == null || !currentGameId.equals(gameId)) {
                    cancel();
                    return;
                }
                if (timeRemaining <= 0) {
                    startEvent();
                    cancel();
                    countdownTask = null;
                    return;
                }

                plugin.broadcastMessageToWorld(worldName, "fivem.countdown", msg -> msg.replace("{time}", String.valueOf(timeRemaining)));
                timeRemaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startEvent() {
        active = true;
        if (currentGameId == null) {
            currentGameId = UUID.randomUUID();
        }
        activePlayers.clear();
        originalStates.clear();
        roundNumber = 0;
        currentDuration = config.getDouble("initial-round-duration-seconds", 5.0);

        String worldName = getWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            active = false;
            return;
        }

        world.setPVP(config.getBoolean("pvp", true));

        // Scan and save platform blocks
        savePlatformBlocks(world);

        for (Player p : world.getPlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            activePlayers.add(p.getUniqueId());
        }

        plugin.broadcastMessageToWorld(worldName, "fivem.started");
        nextRound();
    }

    private void nextRound() {
        if (!active) return;

        if (activePlayers.size() <= 1) {
            checkWinner();
            return;
        }

        // Restore platform blocks first
        restorePlatformBlocks();

        roundNumber++;
        double initial = config.getDouble("initial-round-duration-seconds", 5.0);
        double minDur = config.getDouble("min-round-duration-seconds", 1.5);
        double reduction = config.getDouble("round-duration-reduction-per-round", 0.5);

        currentDuration = Math.max(minDur, initial - (roundNumber - 1) * reduction);

        // Find available colors on the platform
        Set<String> colorsOnPlatform = getColorsOnPlatform();
        if (colorsOnPlatform.isEmpty()) {
            plugin.broadcastMessageToWorld(getWorldName(), "fivem.no_winner");
            stop();
            return;
        }

        List<String> list = new ArrayList<>(colorsOnPlatform);
        String targetColor = list.get(new Random().nextInt(list.size()));
        String friendlyColorName = COLOR_NAMES.getOrDefault(targetColor, targetColor);

        String worldName = getWorldName();
        plugin.broadcastMessageToWorld(worldName, "fivem.round_start", msg -> msg.replace("{round}", String.valueOf(roundNumber))
                .replace("{color}", friendlyColorName)
                .replace("{time}", String.format(Locale.US, "%.1f", currentDuration)));

        long durationTicks = (long) (currentDuration * 20.0);
        final UUID gameId = currentGameId;

        gameTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!active || currentGameId == null || !currentGameId.equals(gameId)) return;

                // Remove non-matching blocks
                removeNonMatchingBlocks(targetColor);
                plugin.broadcastMessageToWorld(worldName, "fivem.blocks_disappeared");

                // Wait 3 seconds for players to fall, then eliminate
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!active || currentGameId == null || !currentGameId.equals(gameId)) return;

                        double minPlatformY = getMinPlatformY();

                        Iterator<UUID> it = activePlayers.iterator();
                        while (it.hasNext()) {
                            UUID uuid = it.next();
                            Player p = Bukkit.getPlayer(uuid);
                            if (p == null || !p.isOnline()) {
                                it.remove();
                                continue;
                            }

                            if (p.getLocation().getY() < minPlatformY - 1.0) {
                                it.remove();
                                eliminatePlayerNoRemove(p);
                            }
                        }

                        if (activePlayers.size() <= 1) {
                            checkWinner();
                            return;
                        }

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (!active || currentGameId == null || !currentGameId.equals(gameId)) return;
                                nextRound();
                            }
                        }.runTaskLater(plugin, 40L);
                    }
                }.runTaskLater(plugin, 60L);
            }
        }.runTaskLater(plugin, durationTicks);
    }

    private void eliminatePlayer(Player p) {
        activePlayers.remove(p.getUniqueId());
        Location spawn = getSpawnLocation();
        if (spawn != null) {
            p.teleport(spawn);
            p.setFallDistance(0.0f);
        }
        plugin.broadcastMessageToWorld(getWorldName(), "fivem.player_eliminated", msg -> msg.replace("{player}", p.getName()));
        if (activePlayers.size() <= 1) {
            checkWinner();
        }
    }

    private void eliminatePlayerNoRemove(Player p) {
        Location spawn = getSpawnLocation();
        if (spawn != null) {
            p.teleport(spawn);
            p.setFallDistance(0.0f);
        }
        plugin.broadcastMessageToWorld(getWorldName(), "fivem.player_eliminated", msg -> msg.replace("{player}", p.getName()));
    }

    private void checkWinner() {
        if (!active) return;
        if (activePlayers.size() == 1) {
            UUID winnerUUID = activePlayers.iterator().next();
            Player winner = Bukkit.getPlayer(winnerUUID);
            if (winner != null) {
                plugin.broadcastMessageToWorld(getWorldName(), "fivem.winner", msg -> msg.replace("{player}", winner.getName()));
                Location spawn = getSpawnLocation();
                if (spawn != null) {
                    winner.teleport(spawn);
                    winner.setFallDistance(0.0f);
                }
            }
        } else {
            plugin.broadcastMessageToWorld(getWorldName(), "fivem.no_winner");
        }
        stop();
    }

    public void stop() {
        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }
        if (gameTask != null) {
            gameTask.cancel();
            gameTask = null;
        }

        currentGameId = null;
        boolean wasActive = active;
        active = false;

        World world = Bukkit.getWorld(getWorldName());
        if (world != null) {
            world.setPVP(false);
            restorePlatformBlocks();
            if (wasActive) {
                plugin.broadcastMessageToWorld(getWorldName(), "fivem.stopped");
            }
        }
        originalStates.clear();
        activePlayers.clear();
    }

    private Set<String> getColorsOnPlatform() {
        Set<String> colors = new HashSet<>();
        for (Location loc : originalStates.keySet()) {
            Material mat = originalStates.get(loc).getType();
            String color = getColorFromMaterial(mat);
            if (color != null) {
                colors.add(color);
            }
        }
        return colors;
    }

    private String getColorFromMaterial(Material mat) {
        String name = mat.name();
        for (String c : COLOR_NAMES.keySet()) {
            if (name.startsWith(c + "_")) {
                return c;
            }
        }
        return null;
    }

    private void savePlatformBlocks(World world) {
        if (!config.isConfigurationSection("arena-region")) return;
        String fromStr = config.getString("arena-region.from");
        String toStr = config.getString("arena-region.to");
        if (fromStr != null && toStr != null) {
            try {
                String[] f = fromStr.split(",");
                String[] t = toStr.split(",");
                int x1 = Integer.parseInt(f[0].trim());
                int y1 = Integer.parseInt(f[1].trim());
                int z1 = Integer.parseInt(f[2].trim());
                int x2 = Integer.parseInt(t[0].trim());
                int y2 = Integer.parseInt(t[1].trim());
                int z2 = Integer.parseInt(t[2].trim());

                int minX = Math.min(x1, x2);
                int maxX = Math.max(x1, x2);
                int minY = Math.min(y1, y2);
                int maxY = Math.max(y1, y2);
                int minZ = Math.min(z1, z2);
                int maxZ = Math.max(z1, z2);

                for (int x = minX; x <= maxX; x++) {
                    for (int y = minY; y <= maxY; y++) {
                        for (int z = minZ; z <= maxZ; z++) {
                            Location loc = new Location(world, x, y, z);
                            Material mat = loc.getBlock().getType();
                            if (getColorFromMaterial(mat) != null) {
                                originalStates.put(loc, loc.getBlock().getState());
                            }
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private void removeNonMatchingBlocks(String targetColor) {
        for (Location loc : originalStates.keySet()) {
            Material mat = originalStates.get(loc).getType();
            String color = getColorFromMaterial(mat);
            if (color != null && !color.equalsIgnoreCase(targetColor)) {
                loc.getBlock().setType(Material.AIR);
            }
        }
    }

    private void restorePlatformBlocks() {
        for (Map.Entry<Location, BlockState> entry : originalStates.entrySet()) {
            entry.getValue().update(true, false);
        }
    }

    private double getMinPlatformY() {
        double minY = 256;
        for (Location loc : originalStates.keySet()) {
            if (loc.getY() < minY) {
                minY = loc.getY();
            }
        }
        return minY;
    }

    public Location getSpawnLocation() {
        World world = Bukkit.getWorld(getWorldName());
        if (world == null) return null;
        double x = config.getDouble("spawn-location.x", 0.5);
        double y = config.getDouble("spawn-location.y", 65.0);
        double z = config.getDouble("spawn-location.z", 0.5);
        float yaw = (float) config.getDouble("spawn-location.yaw", 0.0);
        float pitch = (float) config.getDouble("spawn-location.pitch", 0.0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    public ItemStack createEditorItem(String region) {
        String path = "editor-item." + region;
        String defaultMat = region.equals("arena") ? "GOLDEN_AXE" : "BLAZE_ROD";
        String defaultName = region.equals("arena") ? "&#FFC107&lFiveM Arena Region Editor" : "&#4AA3FF&lFiveM Spawn Location Editor";

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
                if (region.equals("arena")) {
                    lore.add("&7Ляв клик: Точка 1");
                    lore.add("&7Десен клик: Точка 2");
                } else {
                    lore.add("&7Кликни върху блок за задаване на спаун");
                }
            }
            List<String> coloredLore = new ArrayList<>();
            for (String l : lore) {
                coloredLore.add(DeltaEvents.color(l));
            }
            meta.setLore(coloredLore);

            NamespacedKey key = new NamespacedKey(plugin, "fivem_editor");
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

    public String getFiveMEditorType(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        if (!item.hasItemMeta()) return null;
        NamespacedKey key = new NamespacedKey(plugin, "fivem_editor");
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (activePlayers.contains(player.getUniqueId())) {
            eliminatePlayer(player);
        }
        point1.remove(player.getUniqueId());
        point2.remove(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        if (!active) return;
        Player player = event.getEntity();
        if (!activePlayers.contains(player.getUniqueId())) return;

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (active && activePlayers.contains(player.getUniqueId())) {
                eliminatePlayer(player);
            }
        });
    }

    @EventHandler
    public void onPlayerChangedWorld(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (activePlayers.contains(player.getUniqueId())) {
            String worldName = getWorldName();
            if (!player.getWorld().getName().equalsIgnoreCase(worldName)) {
                eliminatePlayer(player);
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        String regionType = getFiveMEditorType(item);
        if (regionType == null) return;

        event.setCancelled(true);

        if (!DeltaEvents.hasAnyPermission(player, "DeltaEvents.admin")) {
            plugin.sendMessage(player, "fivem.no_permission");
            return;
        }

        if (event.getClickedBlock() == null) return;
        Location loc = event.getClickedBlock().getLocation();

        if (regionType.equals("spawn")) {
            Location spawnLoc = loc.clone().add(0.5, 1.0, 0.5);
            spawnLoc.setYaw(player.getLocation().getYaw());
            spawnLoc.setPitch(player.getLocation().getPitch());

            config.set("spawn-location.x", spawnLoc.getX());
            config.set("spawn-location.y", spawnLoc.getY());
            config.set("spawn-location.z", spawnLoc.getZ());
            config.set("spawn-location.yaw", (double) spawnLoc.getYaw());
            config.set("spawn-location.pitch", (double) spawnLoc.getPitch());

            try {
                config.save(fiveMFile);
                player.sendMessage(DeltaEvents.color(plugin.msg("fivem.editor_saved").replace("{region}", "spawn")));
                reload();
            } catch (Exception e) {
                player.sendMessage(DeltaEvents.color("&c[FiveM] Failed to save spawn location: " + e.getMessage()));
            }
        } else if (regionType.equals("arena")) {
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                point1.put(player.getUniqueId(), loc);
                player.sendMessage(DeltaEvents.color(plugin.msg("fivem.editor_point1_set")
                        .replace("{x}", String.valueOf(loc.getBlockX()))
                        .replace("{y}", String.valueOf(loc.getBlockY()))
                        .replace("{z}", String.valueOf(loc.getBlockZ()))
                        .replace("{region}", "arena")));
                checkAndSaveRegion(player);
            } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                point2.put(player.getUniqueId(), loc);
                player.sendMessage(DeltaEvents.color(plugin.msg("fivem.editor_point2_set")
                        .replace("{x}", String.valueOf(loc.getBlockX()))
                        .replace("{y}", String.valueOf(loc.getBlockY()))
                        .replace("{z}", String.valueOf(loc.getBlockZ()))
                        .replace("{region}", "arena")));
                checkAndSaveRegion(player);
            }
        }
    }

    private void checkAndSaveRegion(Player player) {
        Location loc1 = point1.get(player.getUniqueId());
        Location loc2 = point2.get(player.getUniqueId());
        if (loc1 == null || loc2 == null) return;

        String fromStr = String.format("%d,%d,%d", loc1.getBlockX(), loc1.getBlockY(), loc1.getBlockZ());
        String toStr = String.format("%d,%d,%d", loc2.getBlockX(), loc2.getBlockY(), loc2.getBlockZ());

        config.set("arena-region.from", fromStr);
        config.set("arena-region.to", toStr);

        try {
            config.save(fiveMFile);
            player.sendMessage(DeltaEvents.color(plugin.msg("fivem.editor_saved").replace("{region}", "arena")));
            reload();
        } catch (Exception e) {
            player.sendMessage(DeltaEvents.color("&c[FiveM] Failed to save region config: " + e.getMessage()));
        }

        point1.remove(player.getUniqueId());
        point2.remove(player.getUniqueId());
    }
}
