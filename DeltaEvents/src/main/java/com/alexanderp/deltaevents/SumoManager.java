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
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class SumoManager implements Listener {
    private final DeltaEvents plugin;
    private File sumoFile;
    private FileConfiguration config;

    private boolean active = false;
    private final Set<UUID> activePlayers = new HashSet<>();
    private BukkitTask countdownTask = null;
    private final Map<Location, BlockState> originalStates = new HashMap<>();

    // Editor coordinates mapping
    private final Map<UUID, Location> point1 = new HashMap<>();
    private final Map<UUID, Location> point2 = new HashMap<>();

    public SumoManager(DeltaEvents plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        stop();
        if (sumoFile == null) {
            sumoFile = new File(plugin.getDataFolder(), "Sumo.yml");
        }
        if (!sumoFile.exists()) {
            plugin.saveResource("Sumo.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(sumoFile);
        plugin.debug("sumo", "Sumo configuration reloaded.");
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
                sender.sendMessage(getMessage("sumo.already_running"));
            }
            return;
        }

        String worldName = getWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            if (sender != null) {
                sender.sendMessage(DeltaEvents.color(plugin.msg("sumo.world_not_loaded").replace("{world}", worldName)));
            } else {
                plugin.getLogger().warning(DeltaEvents.color(plugin.msg("sumo.world_not_loaded").replace("{world}", worldName)));
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

                broadcastMessage("sumo.countdown", msg -> msg.replace("{time}", String.valueOf(timeRemaining)));
                broadcastSound("countdown");
                timeRemaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void startEvent() {
        active = true;
        activePlayers.clear();

        String worldName = getWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            active = false;
            return;
        }

        // Enable PvP based on configuration (defaults to true)
        world.setPVP(config.getBoolean("pvp", true));

        // 1. Play start sound & message
        broadcastMessage("sumo.started");
        broadcastSound("start");

        // 2. Remove floor blocks and schedule restoration
        originalStates.clear();
        List<Location> floorBlocks = parseFloorBlocks(world);
        for (Location loc : floorBlocks) {
            originalStates.put(loc, loc.getBlock().getState());
            loc.getBlock().setType(Material.AIR);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                restoreBlocksImmediately();
            }
        }.runTaskLater(plugin, 40L); // 2 seconds

        // 3. Register players and give stick (pinned to slot 0)
        ItemStack stick = createStick();
        for (Player p : world.getPlayers()) {
            // Ignore staff members in Creative or Spectator mode
            if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }

            // Check if player is standing within the configured participant region
            if (isInParticipantRegion(p, world)) {
                activePlayers.add(p.getUniqueId());
                p.getInventory().setItem(0, stick);
                p.getInventory().setHeldItemSlot(0);
            }
        }
    }

    private boolean isInParticipantRegion(Player p, World world) {
        if (!p.getWorld().equals(world)) return false;

        if (config.isConfigurationSection("participant-region")) {
            String fromStr = config.getString("participant-region.from");
            String toStr = config.getString("participant-region.to");
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
                        double maxX = Math.max(x1, x2);
                        double minY = Math.min(y1, y2);
                        double maxY = Math.max(y1, y2);
                        double minZ = Math.min(z1, z2);
                        double maxZ = Math.max(z1, z2);

                        double px = p.getLocation().getX();
                        double py = p.getLocation().getY();
                        double pz = p.getLocation().getZ();

                        return px >= minX && px <= maxX &&
                               py >= minY && py <= maxY &&
                               pz >= minZ && pz <= maxZ;
                    }
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Failed to parse participant-region range in Sumo.yml: " + fromStr + " -> " + toStr);
                }
            }
        }
        return false;
    }

    public void stop() {
        restoreBlocksImmediately();

        boolean wasActive = active;
        boolean wasStarting = (countdownTask != null);

        if (countdownTask != null) {
            countdownTask.cancel();
            countdownTask = null;
        }

        active = false;

        if (wasActive || wasStarting) {
            if (config != null) {
                String worldName = getWorldName();
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    for (Player p : world.getPlayers()) {
                        removeStick(p);
                    }

                    if (wasActive) {
                        broadcastMessage("sumo.stopped");
                        broadcastSound("stop");
                    }
                }
            }
        }

        activePlayers.clear();

        if (config != null) {
            String worldName = getWorldName();
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                world.setPVP(false);
            }
        }
    }



    public void startRound(int roundNumber) {
        String titleStr = plugin.getLang().getString("sumo.round_title", "&#4AA3FF&lРУНД {round}").replace("{round}", String.valueOf(roundNumber));
        String subtitleStr = plugin.getLang().getString("sumo.round_subtitle", "&#E6E6E6Пригответе се!");
        String coloredTitle = DeltaEvents.color(titleStr);
        String coloredSubtitle = DeltaEvents.color(subtitleStr);

        String worldName = getWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            for (Player p : world.getPlayers()) {
                p.sendTitle(coloredTitle, coloredSubtitle, 10, 60, 10);
                plugin.playSound(p, config.getString("sounds.round", "round"), 1.0f, 1.0f);
            }
        }
    }

    private void restoreBlocksImmediately() {
        if (!originalStates.isEmpty()) {
            for (Map.Entry<Location, BlockState> entry : originalStates.entrySet()) {
                entry.getValue().update(true, false);
            }
            originalStates.clear();
        }
    }

    private List<Location> parseFloorBlocks(World world) {
        List<Location> locs = new ArrayList<>();
        if (config.isConfigurationSection("floor-blocks")) {
            String fromStr = config.getString("floor-blocks.from");
            String toStr = config.getString("floor-blocks.to");
            if (fromStr != null && toStr != null) {
                try {
                    String[] fromParts = fromStr.split(",");
                    String[] toParts = toStr.split(",");
                    if (fromParts.length >= 3 && toParts.length >= 3) {
                        int x1 = Integer.parseInt(fromParts[0].trim());
                        int y1 = Integer.parseInt(fromParts[1].trim());
                        int z1 = Integer.parseInt(fromParts[2].trim());

                        int x2 = Integer.parseInt(toParts[0].trim());
                        int y2 = Integer.parseInt(toParts[1].trim());
                        int z2 = Integer.parseInt(toParts[2].trim());

                        int minX = Math.min(x1, x2);
                        int maxX = Math.max(x1, x2);
                        int minY = Math.min(y1, y2);
                        int maxY = Math.max(y1, y2);
                        int minZ = Math.min(z1, z2);
                        int maxZ = Math.max(z1, z2);

                        for (int x = minX; x <= maxX; x++) {
                            for (int y = minY; y <= maxY; y++) {
                                for (int z = minZ; z <= maxZ; z++) {
                                    locs.add(new Location(world, x, y, z));
                                }
                            }
                        }
                    }
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Failed to parse floor block range in Sumo.yml: " + fromStr + " -> " + toStr);
                }
            }
        } else {
            List<String> list = config.getStringList("floor-blocks");
            for (String s : list) {
                try {
                    String[] parts = s.split(",");
                    if (parts.length >= 3) {
                        int x = Integer.parseInt(parts[0].trim());
                        int y = Integer.parseInt(parts[1].trim());
                        int z = Integer.parseInt(parts[2].trim());
                        locs.add(new Location(world, x, y, z));
                    }
                } catch (NumberFormatException e) {
                    plugin.getLogger().warning("Failed to parse floor block coordinate in Sumo.yml: " + s);
                }
            }
        }
        return locs;
    }

    public Location getSpawnLocation(World world) {
        double x = config.getDouble("spawn-location.x", 0.5);
        double y = config.getDouble("spawn-location.y", 65.0);
        double z = config.getDouble("spawn-location.z", 0.5);
        float yaw = (float) config.getDouble("spawn-location.yaw", 0.0);
        float pitch = (float) config.getDouble("spawn-location.pitch", 0.0);
        return new Location(world, x, y, z, yaw, pitch);
    }

    public ItemStack createStick() {
        String matName = config.getString("stick.material", "BREEZE_ROD");
        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = Material.BREEZE_ROD;

        ItemStack stick = new ItemStack(mat);
        ItemMeta meta = stick.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(DeltaEvents.color(config.getString("stick.name", "&#4AA3FF&lСумо Пръчка")));

            List<String> lore = config.getStringList("stick.lore");
            List<String> coloredLore = new ArrayList<>();
            for (String l : lore) {
                coloredLore.add(DeltaEvents.color(l));
            }
            meta.setLore(coloredLore);

            meta.addEnchant(org.bukkit.enchantments.Enchantment.KNOCKBACK, 3, true);

            NamespacedKey key = new NamespacedKey(plugin, "sumo_stick");
            int nbtVal = config.getInt("stick.nbt-data", 100);
            meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, nbtVal);
            meta.setCustomModelData(nbtVal);

            stick.setItemMeta(meta);
        }
        return stick;
    }

    public boolean isSumoStick(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }
        if (!item.hasItemMeta()) {
            return false;
        }
        NamespacedKey key = new NamespacedKey(plugin, "sumo_stick");
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.INTEGER);
    }

    public void removeStick(Player player) {
        if (player == null) return;

        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isSumoStick(contents[i])) {
                player.getInventory().setItem(i, null);
            }
        }
        if (isSumoStick(player.getInventory().getItemInOffHand())) {
            player.getInventory().setItemInOffHand(null);
        }
        if (isSumoStick(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
        }
    }

    private void eliminatePlayer(Player player) {
        activePlayers.remove(player.getUniqueId());

        String worldName = getWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            Location spawnLoc = getSpawnLocation(world);
            if (spawnLoc != null) {
                player.teleport(spawnLoc);
                player.setFallDistance(0.0f);
            }
        }

        broadcastMessage("sumo.eliminated", msg -> msg.replace("{player}", player.getName()));
        broadcastSound("notification");
        checkWinner();
    }

    private void handlePlayerLeave(Player player) {
        activePlayers.remove(player.getUniqueId());
        plugin.debug("sumo", "Player " + player.getName() + " left the Sumo event.");
        checkWinner();
    }

    private void checkWinner() {
        if (!active) return;
        if (activePlayers.size() == 1) {
            UUID winnerUUID = activePlayers.iterator().next();
            Player winner = Bukkit.getPlayer(winnerUUID);
            if (winner != null) {
                declareWinner(winner);
            }
        } else if (activePlayers.isEmpty()) {
            broadcastMessage("sumo.no_winner");
            stop();
        }
    }

    private void declareWinner(Player winner) {
        broadcastMessage("sumo.winner", msg -> msg.replace("{player}", winner.getName()));

        String titleStr = plugin.getLang().getString("sumo.winner_title", "&#4CFF7A&lПОБЕДИТЕЛ!");
        String subtitleStr = plugin.getLang().getString("sumo.winner_subtitle", "&f{player} спечели Сумото!");
        String coloredTitle = DeltaEvents.color(titleStr);
        String coloredSubtitle = DeltaEvents.color(subtitleStr.replace("{player}", winner.getName()));

        String worldName = getWorldName();
        World world = Bukkit.getWorld(worldName);
        if (world != null) {
            for (Player p : world.getPlayers()) {
                p.sendTitle(coloredTitle, coloredSubtitle, 10, 80, 20);
            }
            Location spawnLoc = getSpawnLocation(world);
            if (spawnLoc != null && winner.isOnline()) {
                winner.teleport(spawnLoc);
                winner.setFallDistance(0.0f);
            }
        }

        broadcastSound("win");
        stop();
    }

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
        String sumoPrefix = plugin.getLang().getString("sumo.prefix", "");

        replaced = replaced.replace("{prefix}", prefix).replace("{sumo_prefix}", sumoPrefix);
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

    // --- Event Listeners ---

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (!active) return;
        Player player = event.getPlayer();
        if (!activePlayers.contains(player.getUniqueId())) return;

        String worldName = getWorldName();
        if (!player.getWorld().getName().equalsIgnoreCase(worldName)) {
            handlePlayerLeave(player);
            return;
        }

        // Check block below feet
        Location feetLoc = player.getLocation().clone().subtract(0, 0.1, 0);
        Material blockType = feetLoc.getBlock().getType();

        List<String> elimBlocks = config.getStringList("elimination-blocks");
        for (String mName : elimBlocks) {
            if (blockType.name().equalsIgnoreCase(mName)) {
                eliminatePlayer(player);
                break;
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (activePlayers.contains(player.getUniqueId())) {
            handlePlayerLeave(player);
        }
        point1.remove(player.getUniqueId());
        point2.remove(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (activePlayers.contains(player.getUniqueId())) {
            String worldName = getWorldName();
            if (!player.getWorld().getName().equalsIgnoreCase(worldName)) {
                handlePlayerLeave(player);
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!active) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        // Check world
        String worldName = getWorldName();
        if (!victim.getWorld().getName().equalsIgnoreCase(worldName)) return;

        // Resolve attacker player (either direct or shooter of projectile)
        Player attacker = null;
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }

        // If it's a player attack (direct or projectile)
        if (attacker != null) {
            // Check if both are active players in the event
            if (activePlayers.contains(attacker.getUniqueId()) && activePlayers.contains(victim.getUniqueId())) {
                // If it was a projectile attack, cancel it (e.g., bows, pearls, snowballs)
                if (event.getDamager() instanceof Projectile) {
                    event.setCancelled(true);
                    return;
                }

                // Check weapon
                ItemStack weapon = attacker.getInventory().getItemInMainHand();
                if (isSumoStick(weapon) || weapon == null || weapon.getType().isAir()) {
                    // Cancel/reduce damage to 0 so knockback applies but player takes no damage
                    event.setDamage(0);
                } else {
                    // Block attack if not using Sumo stick or empty hand
                    event.setCancelled(true);
                }
            } else {
                // Cancel damage if either attacker or victim is not an active participant (spectator protection)
                event.setCancelled(true);
            }
        } else {
            // Cancel other entity damage (e.g. from mobs, tnt, etc.) in the event world during the event
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(org.bukkit.event.entity.PlayerDeathEvent event) {
        if (!active) return;
        Player player = event.getEntity();
        if (!activePlayers.contains(player.getUniqueId())) return;

        event.getDrops().removeIf(this::isSumoStick);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (active && activePlayers.contains(player.getUniqueId())) {
                eliminatePlayer(player);
            }
        });
    }

    @EventHandler
    public void onEntityDamageGeneric(EntityDamageEvent event) {
        if (!active) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!activePlayers.contains(player.getUniqueId())) return;

        // Cancel generic damage (fall, fire, void, starvation, etc.)
        // But allow ENTITY_ATTACK so knockback logic gets processed in the other event handler
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK &&
            event.getCause() != EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK &&
            event.getCause() != EntityDamageEvent.DamageCause.PROJECTILE) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (!active) return;
        Player player = event.getPlayer();
        if (!activePlayers.contains(player.getUniqueId())) return;

        if (isSumoStick(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerSwapHand(PlayerSwapHandItemsEvent event) {
        if (!active) return;
        Player player = event.getPlayer();
        if (!activePlayers.contains(player.getUniqueId())) return;

        if (isSumoStick(event.getMainHandItem()) || isSumoStick(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!active) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!activePlayers.contains(player.getUniqueId())) return;

        if (isSumoStick(event.getCurrentItem()) || isSumoStick(event.getCursor())) {
            event.setCancelled(true);
            return;
        }

        if (event.getHotbarButton() == 0) {
            event.setCancelled(true);
            return;
        }

        if (event.getSlot() == 0 && event.getClickedInventory() != null && event.getClickedInventory().equals(player.getInventory())) {
            event.setCancelled(true);
            return;
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!active) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!activePlayers.contains(player.getUniqueId())) return;

        if (isSumoStick(event.getOldCursor()) || isSumoStick(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPrepareCraft(org.bukkit.event.inventory.PrepareItemCraftEvent event) {
        if (!active) return;
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (isSumoStick(item)) {
                event.getInventory().setResult(null);
                break;
            }
        }
    }

    // --- Sumo Editor Support ---

    public ItemStack createEditorItem(String region) {
        String path = "editor-item." + region;
        String defaultMat = "GOLDEN_AXE";
        if (region.equals("participant")) {
            defaultMat = "BLAZE_ROD";
        } else if (region.equals("floor")) {
            defaultMat = "GOLDEN_HOE";
        }

        String defaultName = "&#FFC107&lSumo Spawn Location Editor";
        if (region.equals("participant")) {
            defaultName = "&#4AA3FF&lSumo Participant Region Editor";
        } else if (region.equals("floor")) {
            defaultName = "&#4CFF7A&lSumo Floor Blocks Editor";
        }

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
                if (region.equals("participant") || region.equals("floor")) {
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

            NamespacedKey key = new NamespacedKey(plugin, "sumo_editor");
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

    public String getSumoEditorType(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;
        if (!item.hasItemMeta()) return null;
        NamespacedKey key = new NamespacedKey(plugin, "sumo_editor");
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        String regionType = getSumoEditorType(item);
        if (regionType == null) return;

        event.setCancelled(true);

        // Check permission when using the editor wand
        if (!DeltaEvents.hasAnyPermission(player, "DeltaEvents.sumo.admin", "DeltaEvents.admin")) {
            player.sendMessage(getMessage("sumo.no_permission"));
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
                config.save(sumoFile);
                player.sendMessage(DeltaEvents.color(plugin.msg("sumo.editor_saved").replace("{region}", "spawn")));
                reload();
            } catch (Exception e) {
                player.sendMessage(DeltaEvents.color("&c[Sumo] Failed to save spawn location: " + e.getMessage()));
            }
        } else if (regionType.equals("participant") || regionType.equals("floor")) {
            if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
                point1.put(player.getUniqueId(), loc);
                player.sendMessage(DeltaEvents.color(plugin.msg("sumo.editor_point1_set")
                        .replace("{x}", String.valueOf(loc.getBlockX()))
                        .replace("{y}", String.valueOf(loc.getBlockY()))
                        .replace("{z}", String.valueOf(loc.getBlockZ()))
                        .replace("{region}", regionType)));

                checkAndSaveRegion(player, regionType);
            } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
                point2.put(player.getUniqueId(), loc);
                player.sendMessage(DeltaEvents.color(plugin.msg("sumo.editor_point2_set")
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

        String configKey = regionType.equals("participant") ? "participant-region" : "floor-blocks";
        config.set(configKey + ".from", fromStr);
        config.set(configKey + ".to", toStr);

        try {
            config.save(sumoFile);
            player.sendMessage(DeltaEvents.color(plugin.msg("sumo.editor_saved").replace("{region}", regionType)));
            reload();
        } catch (Exception e) {
            player.sendMessage(DeltaEvents.color("&c[Sumo] Failed to save region config: " + e.getMessage()));
        }

        point1.remove(player.getUniqueId());
        point2.remove(player.getUniqueId());
    }
}
