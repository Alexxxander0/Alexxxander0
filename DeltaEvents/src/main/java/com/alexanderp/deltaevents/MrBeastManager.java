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
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;

public class MrBeastManager implements Listener {
    private final DeltaEvents plugin;
    private File mrBeastFile;
    private FileConfiguration config;

    private boolean active = false;
    private UUID currentGameId = null;
    private final Set<UUID> activePlayers = new HashSet<>();
    private final Set<String> eliminatedColors = new HashSet<>();
    private final Map<Location, BlockState> originalStates = new HashMap<>();

    private BukkitTask countdownTask = null;
    private BukkitTask gameTask = null;

    private int roundNumber = 0;
    private boolean choiceLocked = false;
    private final Map<UUID, Location> lockedLocations = new HashMap<>();
    private final Map<UUID, Location> point1 = new HashMap<>();
    private final Map<UUID, Location> point2 = new HashMap<>();

    public MrBeastManager(DeltaEvents plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        stop();
        if (mrBeastFile == null) {
            mrBeastFile = new File(plugin.getDataFolder(), "MrBeast.yml");
        }
        if (!mrBeastFile.exists()) {
            plugin.saveResource("MrBeast.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(mrBeastFile);
        plugin.debug("mrbeast", "MrBeast configuration reloaded.");
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
                plugin.sendMessage(sender, "mrbeast.already_running");
            }
            return;
        }

        String worldName = getWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            if (sender != null) {
                sender.sendMessage(DeltaEvents.color(plugin.msg("mrbeast.world_not_loaded").replace("{world}", worldName)));
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

                plugin.broadcastMessageToWorld(worldName, "mrbeast.countdown", msg -> msg.replace("{time}", String.valueOf(timeRemaining)));
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
        eliminatedColors.clear();
        originalStates.clear();
        roundNumber = 0;
        choiceLocked = false;
        lockedLocations.clear();

        String worldName = getWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            active = false;
            return;
        }

        world.setPVP(config.getBoolean("pvp", true));

        savePlatformBlocks(world);

        for (Player p : world.getPlayers()) {
            if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            activePlayers.add(p.getUniqueId());
        }

        plugin.broadcastMessageToWorld(worldName, "mrbeast.started");
        nextRound();
    }

    private void nextRound() {
        if (!active) return;

        if (activePlayers.size() <= 1) {
            checkWinner();
            return;
        }

        Set<String> allColors = getAvailableColors();
        allColors.removeAll(eliminatedColors);
        if (allColors.isEmpty()) {
            plugin.broadcastMessageToWorld(getWorldName(), "mrbeast.no_winner");
            stop();
            return;
        }

        roundNumber++;
        choiceLocked = false;
        lockedLocations.clear();

        String worldName = getWorldName();
        int chooseDuration = config.getInt("round-duration-seconds", 15);
        final UUID gameId = currentGameId;

        plugin.broadcastMessageToWorld(worldName, "mrbeast.round_start", msg -> msg.replace("{round}", String.valueOf(roundNumber)).replace("{time}", String.valueOf(chooseDuration)));

        gameTask = new BukkitRunnable() {
            int timeRemaining = chooseDuration;

            @Override
            public void run() {
                if (!active || currentGameId == null || !currentGameId.equals(gameId)) {
                    cancel();
                    return;
                }

                if (timeRemaining <= 0) {
                    lockChoices();
                    cancel();
                    return;
                }

                plugin.broadcastMessageToWorld(worldName, "mrbeast.round_countdown", msg -> msg.replace("{time}", String.valueOf(timeRemaining)));
                timeRemaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void lockChoices() {
        choiceLocked = true;
        String worldName = getWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        plugin.broadcastMessageToWorld(worldName, "mrbeast.choices_locked");

        for (UUID uuid : activePlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                lockedLocations.put(uuid, p.getLocation());
            }
        }

        Set<String> available = getAvailableColors();
        available.removeAll(eliminatedColors);

        if (available.isEmpty()) {
            checkWinner();
            return;
        }

        List<String> list = new ArrayList<>(available);
        String toEliminate = list.get(new Random().nextInt(list.size()));
        eliminatedColors.add(toEliminate);

        String colorName = config.getString("platforms." + toEliminate + ".color-name", toEliminate);

        plugin.broadcastMessageToWorld(worldName, "mrbeast.color_eliminated", msg -> msg.replace("{color}", colorName));

        final UUID gameId = currentGameId;

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!active || currentGameId == null || !currentGameId.equals(gameId)) return;

                removePlatformBlocks(world, toEliminate);

                Iterator<UUID> it = activePlayers.iterator();
                while (it.hasNext()) {
                    UUID uuid = it.next();
                    Player p = Bukkit.getPlayer(uuid);
                    if (p == null || !p.isOnline()) {
                        it.remove();
                        continue;
                    }

                    String currentPlatform = getPlayerPlatform(p, world);
                    if (currentPlatform == null || currentPlatform.equalsIgnoreCase(toEliminate)) {
                        it.remove();
                        eliminatePlayerNoRemove(p);
                    }
                }

                choiceLocked = false;
                lockedLocations.clear();

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (!active || currentGameId == null || !currentGameId.equals(gameId)) return;
                        nextRound();
                    }
                }.runTaskLater(plugin, 60L);
            }
        }.runTaskLater(plugin, 60L);
    }

    private void eliminatePlayer(Player p) {
        activePlayers.remove(p.getUniqueId());
        Location spawn = getSpawnLocation();
        if (spawn != null) {
            p.teleport(spawn);
            p.setFallDistance(0.0f);
        }
        plugin.broadcastMessageToWorld(getWorldName(), "mrbeast.player_eliminated", msg -> msg.replace("{player}", p.getName()));
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
        plugin.broadcastMessageToWorld(getWorldName(), "mrbeast.player_eliminated", msg -> msg.replace("{player}", p.getName()));
    }

    private void checkWinner() {
        if (!active) return;
        if (activePlayers.size() == 1) {
            UUID winnerUUID = activePlayers.iterator().next();
            Player winner = Bukkit.getPlayer(winnerUUID);
            if (winner != null) {
                plugin.broadcastMessageToWorld(getWorldName(), "mrbeast.winner", msg -> msg.replace("{player}", winner.getName()));
                Location spawn = getSpawnLocation();
                if (spawn != null) {
                    winner.teleport(spawn);
                    winner.setFallDistance(0.0f);
                }
            }
        } else {
            plugin.broadcastMessageToWorld(getWorldName(), "mrbeast.no_winner");
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
        choiceLocked = false;
        lockedLocations.clear();

        World world = Bukkit.getWorld(getWorldName());
        if (world != null) {
            world.setPVP(false);
            restorePlatformBlocks();
            if (wasActive) {
                plugin.broadcastMessageToWorld(getWorldName(), "mrbeast.stopped");
            }
        }
        activePlayers.clear();
        eliminatedColors.clear();
    }

    private Set<String> getAvailableColors() {
        Set<String> colors = new HashSet<>();
        if (config.isConfigurationSection("platforms")) {
            colors.addAll(config.getConfigurationSection("platforms").getKeys(false));
        }
        return colors;
    }

    private String getPlayerPlatform(Player p, World world) {
        if (!p.getWorld().equals(world)) return null;
        Location loc = p.getLocation();
        double px = loc.getX();
        double py = loc.getY();
        double pz = loc.getZ();

        for (String key : getAvailableColors()) {
            String fromStr = config.getString("platforms." + key + ".from");
            String toStr = config.getString("platforms." + key + ".to");
            if (fromStr != null && toStr != null) {
                try {
                    String[] f = fromStr.split(",");
                    String[] t = toStr.split(",");
                    double x1 = Double.parseDouble(f[0].trim());
                    double y1 = Double.parseDouble(f[1].trim());
                    double z1 = Double.parseDouble(f[2].trim());

                    double x2 = Double.parseDouble(t[0].trim());
                    double y2 = Double.parseDouble(t[1].trim());
                    double z2 = Double.parseDouble(t[2].trim());

                    double minX = Math.min(x1, x2);
                    double maxX = Math.max(x1, x2) + 1.0;
                    double minY = Math.min(y1, y2) - 0.5;
                    double maxY = Math.max(y1, y2) + 1.5;
                    double minZ = Math.min(z1, z2);
                    double maxZ = Math.max(z1, z2) + 1.0;

                    if (px >= minX && px <= maxX && py >= minY && py <= maxY && pz >= minZ && pz <= maxZ) {
                        return key;
                    }
                } catch (Exception ignored) {}
            }
        }
        return null;
    }

    private void savePlatformBlocks(World world) {
        for (String key : getAvailableColors()) {
            String fromStr = config.getString("platforms." + key + ".from");
            String toStr = config.getString("platforms." + key + ".to");
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
                                originalStates.put(loc, loc.getBlock().getState());
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }
    }

    private void removePlatformBlocks(World world, String platformKey) {
        String fromStr = config.getString("platforms." + platformKey + ".from");
        String toStr = config.getString("platforms." + platformKey + ".to");
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
                            new Location(world, x, y, z).getBlock().setType(Material.AIR);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    private void restorePlatformBlocks() {
        for (Map.Entry<Location, BlockState> entry : originalStates.entrySet()) {
            entry.getValue().update(true, false);
        }
        originalStates.clear();
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
        String defaultMat = region.equals("spawn") ? "BLAZE_ROD" : "GOLDEN_AXE";
        String displayNameRegion = region.substring(0, 1).toUpperCase() + region.substring(1);
        String defaultName = region.equals("spawn")
                ? "&#4AA3FF&lMrBeast Spawn Location Editor"
                : "&#FFC107&lMrBeast Platform " + displayNameRegion + " Editor";

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

            NamespacedKey key = new NamespacedKey(plugin, "mrbeast_editor");
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

    public String getMrBeastEditorType(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        if (!item.hasItemMeta()) return null;
        NamespacedKey key = new NamespacedKey(plugin, "mrbeast_editor");
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!active || !choiceLocked) return;
        Player player = event.getPlayer();
        if (!activePlayers.contains(player.getUniqueId())) return;

        Location locked = lockedLocations.get(player.getUniqueId());
        if (locked != null) {
            Location to = event.getTo();
            if (to.getX() != locked.getX() || to.getZ() != locked.getZ()) {
                Location back = locked.clone();
                back.setYaw(to.getYaw());
                back.setPitch(to.getPitch());
                event.setTo(back);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (activePlayers.contains(player.getUniqueId())) {
            eliminatePlayer(player);
        }
        lockedLocations.remove(player.getUniqueId());
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
        String regionType = getMrBeastEditorType(item);
        if (regionType == null) return;

        event.setCancelled(true);

        if (!DeltaEvents.hasAnyPermission(player, "DeltaEvents.admin")) {
            plugin.sendMessage(player, "mrbeast.no_permission");
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
                config.save(mrBeastFile);
                player.sendMessage(DeltaEvents.color(plugin.msg("mrbeast.editor_saved").replace("{region}", "spawn")));
                reload();
            } catch (Exception e) {
                player.sendMessage(DeltaEvents.color("&c[MrBeast] Failed to save spawn location: " + e.getMessage()));
            }
        } else {
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                point1.put(player.getUniqueId(), loc);
                player.sendMessage(DeltaEvents.color(plugin.msg("mrbeast.editor_point1_set")
                        .replace("{x}", String.valueOf(loc.getBlockX()))
                        .replace("{y}", String.valueOf(loc.getBlockY()))
                        .replace("{z}", String.valueOf(loc.getBlockZ()))
                        .replace("{region}", regionType)));
                checkAndSaveRegion(player, regionType);
            } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                point2.put(player.getUniqueId(), loc);
                player.sendMessage(DeltaEvents.color(plugin.msg("mrbeast.editor_point2_set")
                        .replace("{x}", String.valueOf(loc.getBlockX()))
                        .replace("{y}", String.valueOf(loc.getBlockY()))
                        .replace("{z}", String.valueOf(loc.getBlockZ()))
                        .replace("{region}", regionType)));
                checkAndSaveRegion(player, regionType);
            }
        }
    }

    private void checkAndSaveRegion(Player player, String platformKey) {
        Location loc1 = point1.get(player.getUniqueId());
        Location loc2 = point2.get(player.getUniqueId());
        if (loc1 == null || loc2 == null) return;

        String fromStr = String.format("%d,%d,%d", loc1.getBlockX(), loc1.getBlockY(), loc1.getBlockZ());
        String toStr = String.format("%d,%d,%d", loc2.getBlockX(), loc2.getBlockY(), loc2.getBlockZ());

        config.set("platforms." + platformKey + ".from", fromStr);
        config.set("platforms." + platformKey + ".to", toStr);

        try {
            config.save(mrBeastFile);
            player.sendMessage(DeltaEvents.color(plugin.msg("mrbeast.editor_saved").replace("{region}", platformKey)));
            reload();
        } catch (Exception e) {
            player.sendMessage(DeltaEvents.color("&c[MrBeast] Failed to save platform region config: " + e.getMessage()));
        }

        point1.remove(player.getUniqueId());
        point2.remove(player.getUniqueId());
    }
}
