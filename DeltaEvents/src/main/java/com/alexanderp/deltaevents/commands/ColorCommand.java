package com.alexanderp.deltaevents.commands;

import com.alexanderp.deltaevents.DeltaEvents;
import com.alexanderp.deltaevents.MrBeastManager;
import com.alexanderp.deltaevents.FiveMManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ColorCommand implements CommandExecutor, TabCompleter {
    private final DeltaEvents plugin;
    private final MrBeastManager mrBeastManager;
    private final FiveMManager fiveMManager;

    public ColorCommand(DeltaEvents plugin, MrBeastManager mrBeastManager, FiveMManager fiveMManager) {
        this.plugin = plugin;
        this.mrBeastManager = mrBeastManager;
        this.fiveMManager = fiveMManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        String eventType = args[0].toLowerCase();
        String action = args[1].toLowerCase();

        if (!eventType.equals("mrbeast") && !eventType.equals("fivem")) {
            sendUsage(sender);
            return true;
        }

        if (eventType.equals("mrbeast")) {
            return handleMrBeast(sender, action, args);
        } else {
            return handleFiveM(sender, action, args);
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(DeltaEvents.color(plugin.msg("color.usage_header")));
        sender.sendMessage(DeltaEvents.color(plugin.msg("color.usage_title")));
        if (DeltaEvents.hasAnyPermission(sender, "DeltaEvents.admin")) {
            sender.sendMessage(DeltaEvents.color(plugin.msg("color.usage_mrbeast_start")));
            sender.sendMessage(DeltaEvents.color(plugin.msg("color.usage_mrbeast_stop")));
            sender.sendMessage(DeltaEvents.color(plugin.msg("color.usage_mrbeast_editor")));
            sender.sendMessage(DeltaEvents.color(plugin.msg("color.usage_mrbeast_reload")));
            sender.sendMessage(DeltaEvents.color(plugin.msg("color.usage_fivem_start")));
            sender.sendMessage(DeltaEvents.color(plugin.msg("color.usage_fivem_stop")));
            sender.sendMessage(DeltaEvents.color(plugin.msg("color.usage_fivem_editor")));
            sender.sendMessage(DeltaEvents.color(plugin.msg("color.usage_fivem_reload")));
        }
        sender.sendMessage(DeltaEvents.color(plugin.msg("color.usage_footer")));
    }

    private boolean handleMrBeast(CommandSender sender, String action, String[] args) {
        if (!DeltaEvents.hasAnyPermission(sender, "DeltaEvents.admin")) {
            plugin.sendMessage(sender, "mrbeast.no_permission");
            return true;
        }

        switch (action) {
            case "start":
                if (mrBeastManager.isActive() || mrBeastManager.isStarting()) {
                    plugin.sendMessage(sender, "mrbeast.already_running");
                    return true;
                }
                if (sender instanceof Player player) {
                    mrBeastManager.startCountdown(player);
                } else {
                    mrBeastManager.startCountdown(null);
                }
                plugin.sendMessage(sender, "mrbeast.countdown_started");
                break;
            case "stop":
                if (!mrBeastManager.isActive() && !mrBeastManager.isStarting()) {
                    plugin.sendMessage(sender, "mrbeast.not_running");
                    return true;
                }
                mrBeastManager.stop();
                plugin.sendMessage(sender, "mrbeast.stop_success");
                break;
            case "reload":
                mrBeastManager.reload();
                plugin.sendMessage(sender, "mrbeast.reload_success");
                break;
            case "editor":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(DeltaEvents.color(plugin.msg("mrbeast.only_players")));
                    return true;
                }
                String region = "spawn";
                if (args.length >= 3) {
                    region = args[2].toLowerCase();
                }
                ItemStack item = mrBeastManager.createEditorItem(region);
                player.getInventory().addItem(item);
                player.sendMessage(DeltaEvents.color(plugin.msg("mrbeast.editor_give_success").replace("{region}", region)));
                break;
            default:
                sendUsage(sender);
                break;
        }
        return true;
    }

    private boolean handleFiveM(CommandSender sender, String action, String[] args) {
        if (!DeltaEvents.hasAnyPermission(sender, "DeltaEvents.admin")) {
            plugin.sendMessage(sender, "fivem.no_permission");
            return true;
        }

        switch (action) {
            case "start":
                if (fiveMManager.isActive() || fiveMManager.isStarting()) {
                    plugin.sendMessage(sender, "fivem.already_running");
                    return true;
                }
                if (sender instanceof Player player) {
                    fiveMManager.startCountdown(player);
                } else {
                    fiveMManager.startCountdown(null);
                }
                plugin.sendMessage(sender, "fivem.countdown_started");
                break;
            case "stop":
                if (!fiveMManager.isActive() && !fiveMManager.isStarting()) {
                    plugin.sendMessage(sender, "fivem.not_running");
                    return true;
                }
                fiveMManager.stop();
                plugin.sendMessage(sender, "fivem.stop_success");
                break;
            case "reload":
                fiveMManager.reload();
                plugin.sendMessage(sender, "fivem.reload_success");
                break;
            case "editor":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(DeltaEvents.color(plugin.msg("mrbeast.only_players")));
                    return true;
                }
                String regionF = "arena";
                if (args.length >= 3) {
                    String opt = args[2].toLowerCase();
                    if (opt.equals("spawn") || opt.equals("arena")) {
                        regionF = opt;
                    }
                }
                ItemStack editorItem = fiveMManager.createEditorItem(regionF);
                player.getInventory().addItem(editorItem);
                player.sendMessage(DeltaEvents.color(plugin.msg("fivem.editor_give_success").replace("{region}", regionF)));
                break;
            default:
                sendUsage(sender);
                break;
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> events = Arrays.asList("mrbeast", "fivem");
            StringUtil.copyPartialMatches(args[0], events, completions);
            Collections.sort(completions);
        } else if (args.length == 2) {
            List<String> actions = Arrays.asList("start", "stop", "editor", "reload");
            StringUtil.copyPartialMatches(args[1], actions, completions);
            Collections.sort(completions);
        } else if (args.length == 3 && args[1].equalsIgnoreCase("editor")) {
            if (args[0].equalsIgnoreCase("fivem")) {
                List<String> targets = Arrays.asList("arena", "spawn");
                StringUtil.copyPartialMatches(args[2], targets, completions);
                Collections.sort(completions);
            } else if (args[0].equalsIgnoreCase("mrbeast")) {
                List<String> targets = new ArrayList<>();
                targets.add("spawn");
                if (mrBeastManager.getConfig().isConfigurationSection("platforms")) {
                    targets.addAll(mrBeastManager.getConfig().getConfigurationSection("platforms").getKeys(false));
                }
                StringUtil.copyPartialMatches(args[2], targets, completions);
                Collections.sort(completions);
            }
        }
        return completions;
    }
}
