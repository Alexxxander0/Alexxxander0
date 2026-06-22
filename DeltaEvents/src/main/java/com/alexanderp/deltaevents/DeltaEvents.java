package com.alexanderp.deltaevents;

import com.alexanderp.deltaevents.commands.DeltaEventsCommand;
import com.alexanderp.deltaevents.commands.SumoCommand;
import com.alexanderp.deltaevents.commands.KothCommand;
import com.alexanderp.deltaevents.commands.ColorCommand;
import java.io.File;
import java.util.List;
import java.util.ArrayList;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class DeltaEvents extends JavaPlugin {
    private FileConfiguration lang;
    private DeltaEventsCommand mainCommand;
    private SumoManager sumoManager;
    private KothManager kothManager;
    private MrBeastManager mrBeastManager;
    private FiveMManager fiveMManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveLang();
        reloadLang();

        sumoManager = new SumoManager(this);
        getServer().getPluginManager().registerEvents(sumoManager, this);

        kothManager = new KothManager(this);
        getServer().getPluginManager().registerEvents(kothManager, this);

        mrBeastManager = new MrBeastManager(this);
        getServer().getPluginManager().registerEvents(mrBeastManager, this);

        fiveMManager = new FiveMManager(this);
        getServer().getPluginManager().registerEvents(fiveMManager, this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new KothExpansion(this, kothManager).register();
            debug("startup", "PlaceholderAPI expansion registered successfully");
        }

        mainCommand = new DeltaEventsCommand(this);
        SumoCommand sumoCmd = new SumoCommand(this, sumoManager);
        KothCommand kothCmd = new KothCommand(this, kothManager);
        ColorCommand colorCmd = new ColorCommand(this, mrBeastManager, fiveMManager);

        registerCommand("deltaevents", mainCommand);
        registerCommand("sumo", sumoCmd);
        registerCommand("koth", kothCmd);
        registerCommand("color", colorCmd);

        debug("startup", "Plugin enabled successfully");
    }

    @Override
    public void onDisable() {
        if (sumoManager != null) {
            sumoManager.stop();
        }
        if (kothManager != null) {
            kothManager.stop();
        }
        if (mrBeastManager != null) {
            mrBeastManager.stop();
        }
        if (fiveMManager != null) {
            fiveMManager.stop();
        }
        debug("shutdown", "Plugin disabled successfully");
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(executor);
            if (executor instanceof org.bukkit.command.TabCompleter) {
                command.setTabCompleter((org.bukkit.command.TabCompleter) executor);
            }
            debug("startup", "Registered command /" + name);
        } else {
            getLogger().severe("Command /" + name + " is not registered in plugin.yml!");
        }
    }

    private void saveLang() {
        File langFile = new File(getDataFolder(), "lang.yml");
        if (!langFile.exists()) {
            saveResource("lang.yml", false);
        }
    }

    public void reloadLang() {
        lang = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "lang.yml"));
    }

    public void reloadAll() {
        reloadConfig();
        reloadLang();
        if (sumoManager != null) {
            sumoManager.reload();
        }
        if (kothManager != null) {
            kothManager.reload();
        }
        if (mrBeastManager != null) {
            mrBeastManager.reload();
        }
        if (fiveMManager != null) {
            fiveMManager.reload();
        }
    }

    public FileConfiguration getLang() {
        return lang;
    }

    public String langRaw(String key) {
        FileConfiguration cfg = getLang();
        if (cfg == null) {
            return key;
        }
        String value = cfg.getString(key);
        if (value == null || value.isEmpty()) {
            return key;
        }
        return value;
    }

    public String msg(String key) {
        String value = lang == null ? key : lang.getString(key, key);
        if (value.isEmpty()) {
            return value;
        }
        String prefix = lang == null ? "" : lang.getString("prefix", "");
        String sumoPrefix = lang == null ? "" : lang.getString("sumo.prefix", "");
        String kothPrefix = lang == null ? "" : lang.getString("koth.prefix", "");
        String resolved = value.replace("{prefix}", prefix)
                               .replace("{sumo_prefix}", sumoPrefix)
                               .replace("{koth_prefix}", kothPrefix);
        if ((prefix == null || prefix.isBlank()) && value.startsWith("{prefix}")) {
            resolved = resolved.stripLeading();
        }
        if ((sumoPrefix == null || sumoPrefix.isBlank()) && value.startsWith("{sumo_prefix}")) {
            resolved = resolved.stripLeading();
        }
        if ((kothPrefix == null || kothPrefix.isBlank()) && value.startsWith("{koth_prefix}")) {
            resolved = resolved.stripLeading();
        }
        return resolved;
    }

    public void sendMessage(org.bukkit.command.CommandSender sender, String path, java.util.function.Function<String, String> replacer) {
        if (lang == null) {
            sender.sendMessage(path);
            return;
        }

        if (lang.isConfigurationSection(path)) {
            org.bukkit.configuration.ConfigurationSection section = lang.getConfigurationSection(path);
            if (section == null) return;

            List<String> messages = new ArrayList<>();
            if (section.isList("text")) {
                messages.addAll(section.getStringList("text"));
            } else {
                String text = section.getString("text");
                if (text != null) {
                    messages.add(text);
                }
            }

            if (messages.isEmpty()) return;

            List<String> displays = new ArrayList<>();
            if (section.isList("display")) {
                displays.addAll(section.getStringList("display"));
            } else {
                String display = section.getString("display", "CHAT");
                for (String d : display.split(",")) {
                    displays.add(d.trim().toUpperCase());
                }
            }

            for (String rawMsg : messages) {
                String formatted = formatMessageString(rawMsg, replacer);
                if (formatted == null || formatted.isEmpty()) continue;

                if (!(sender instanceof Player player)) {
                    sender.sendMessage(formatted);
                    continue;
                }

                for (String disp : displays) {
                    dispatchToPlayer(player, formatted, disp, section);
                }
            }
        } else {
            if (lang.isList(path)) {
                for (String rawMsg : lang.getStringList(path)) {
                    String formatted = formatMessageString(rawMsg, replacer);
                    sender.sendMessage(formatted);
                }
            } else {
                String rawMsg = lang.getString(path);
                if (rawMsg == null || rawMsg.isEmpty()) {
                    rawMsg = path;
                }
                String formatted = formatMessageString(rawMsg, replacer);
                sender.sendMessage(formatted);
            }
        }
    }

    public void sendMessage(org.bukkit.command.CommandSender sender, String path) {
        sendMessage(sender, path, null);
    }

    private String formatMessageString(String raw, java.util.function.Function<String, String> replacer) {
        if (raw == null) return null;
        String prefix = lang == null ? "" : lang.getString("prefix", "");
        String sumoPrefix = lang == null ? "" : lang.getString("sumo.prefix", "");
        String kothPrefix = lang == null ? "" : lang.getString("koth.prefix", "");

        String resolved = raw.replace("{prefix}", prefix)
                             .replace("{sumo_prefix}", sumoPrefix)
                             .replace("{koth_prefix}", kothPrefix);

        if (replacer != null) {
            resolved = replacer.apply(resolved);
        }
        return color(resolved);
    }

    private void dispatchToPlayer(Player player, String message, String displayType, org.bukkit.configuration.ConfigurationSection section) {
        switch (displayType) {
            case "TITLE":
                player.sendTitle(message, "", 10, 70, 20);
                break;
            case "SUBTITLE":
                player.sendTitle("", message, 10, 70, 20);
                break;
            case "ACTIONBAR":
            case "ACTION_BAR":
                player.sendActionBar(message);
                break;
            case "BOSSBAR":
            case "BOSS_BAR":
                try {
                    String colorStr = section.getString("color", "BLUE").toUpperCase();
                    String styleStr = section.getString("style", "SOLID").toUpperCase();
                    org.bukkit.boss.BarColor barColor = org.bukkit.boss.BarColor.valueOf(colorStr);
                    org.bukkit.boss.BarStyle barStyle = org.bukkit.boss.BarStyle.valueOf(styleStr);
                    org.bukkit.boss.BossBar bossBar = org.bukkit.Bukkit.createBossBar(message, barColor, barStyle);
                    bossBar.addPlayer(player);
                    new org.bukkit.scheduler.BukkitRunnable() {
                        @Override
                        public void run() {
                            bossBar.removeAll();
                        }
                    }.runTaskLater(this, 100L);
                } catch (Exception e) {
                    player.sendMessage(message);
                }
                break;
            case "CHAT":
            default:
                player.sendMessage(message);
                break;
        }
    }

    public void broadcastMessageToWorld(String worldName, String path, java.util.function.Function<String, String> replacer) {
        World world = getServer().getWorld(worldName);
        if (world == null) return;
        for (Player p : world.getPlayers()) {
            sendMessage(p, path, replacer);
        }
    }

    public void broadcastMessageToWorld(String worldName, String path) {
        broadcastMessageToWorld(worldName, path, null);
    }

    public boolean isDebugEnabled() {
        return getConfig().getBoolean("debug", false);
    }

    public void debug(String area, String message) {
        if (!isDebugEnabled()) {
            return;
        }
        String safeArea = area == null || area.isBlank() ? "" : area.trim() + ": ";
        String text = safeArea + (message == null ? "" : message.trim());
        if (text.isBlank()) {
            return;
        }
        getLogger().info("[Delta Events DEBUG] " + text);
    }

    public static boolean hasAnyPermission(org.bukkit.command.CommandSender sender, String... permissions) {
        if (!(sender instanceof org.bukkit.entity.Player)) {
            return true;
        }
        if (sender.isOp()) {
            return true;
        }
        for (String permission : permissions) {
            if (permission != null && !permission.isBlank() && sender.hasPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    public static String color(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        String output = input;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("&#([A-Fa-f0-9]{6})").matcher(output);
        while (matcher.find()) {
            String hex = matcher.group(1);
            output = output.replace("&#" + hex, ChatColor.of("#" + hex).toString());
        }
        return ChatColor.translateAlternateColorCodes('&', output);
    }
    public String getEventWorldName() {
        return getConfig().getString("event-world", "BuilderUniverse");
    }

    public void playSound(Player player, String alias, float volume, float pitch) {
        if (player == null) return;
        String soundKey = getConfig().getString("sounds." + alias);
        if (soundKey == null || soundKey.isBlank()) {
            soundKey = alias;
        }
        try {
            player.playSound(player.getLocation(), soundKey, volume, pitch);
        } catch (Exception e) {
            debug("sound", "Failed to play sound: " + soundKey + " (" + e.getMessage() + ")");
        }
    }

    public void broadcastSoundToWorld(String worldName, String alias, float volume, float pitch) {
        World world = getServer().getWorld(worldName);
        if (world == null) return;
        for (Player p : world.getPlayers()) {
            playSound(p, alias, volume, pitch);
        }
    }
}
