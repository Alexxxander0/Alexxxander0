package com.alexanderp.deltaevents.commands;

import com.alexanderp.deltaevents.DeltaEvents;
import com.alexanderp.deltaevents.KothManager;
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

public class KothCommand implements CommandExecutor, TabCompleter {
    private final DeltaEvents plugin;
    private final KothManager kothManager;

    public KothCommand(DeltaEvents plugin, KothManager kothManager) {
        this.plugin = plugin;
        this.kothManager = kothManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                org.bukkit.Bukkit.dispatchCommand(org.bukkit.Bukkit.getConsoleSender(), "sudo " + player.getName() + " warp koth");
            }
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "start":
                return handleStart(sender);
            case "stop":
                return handleStop(sender);
            case "editor":
                return handleEditor(sender, args);
            case "reload":
                return handleReload(sender);
            default:
                sendUsage(sender);
                return true;
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(DeltaEvents.color(plugin.msg("koth.usage_header")));
        sender.sendMessage(DeltaEvents.color(plugin.msg("koth.usage_title")));
        if (DeltaEvents.hasAnyPermission(sender, "DeltaEvents.koth.admin", "DeltaEvents.admin")) {
            sender.sendMessage(DeltaEvents.color(plugin.msg("koth.usage_start")));
            sender.sendMessage(DeltaEvents.color(plugin.msg("koth.usage_stop")));
            sender.sendMessage(DeltaEvents.color(plugin.msg("koth.usage_editor")));
            sender.sendMessage(DeltaEvents.color(plugin.msg("koth.usage_reload")));
        }
        sender.sendMessage(DeltaEvents.color(plugin.msg("koth.usage_footer")));
    }

    private boolean handleStart(CommandSender sender) {
        if (!DeltaEvents.hasAnyPermission(sender, "DeltaEvents.koth.admin", "DeltaEvents.admin")) {
            sender.sendMessage(kothManager.getMessage("koth.no_permission"));
            return true;
        }

        if (kothManager.isActive() || kothManager.isStarting()) {
            sender.sendMessage(kothManager.getMessage("koth.already_running"));
            return true;
        }

        if (sender instanceof Player player) {
            kothManager.startCountdown(player);
        } else {
            kothManager.startCountdown(null);
            sender.sendMessage(DeltaEvents.color(plugin.msg("koth.countdown_started")));
        }
        return true;
    }

    private boolean handleStop(CommandSender sender) {
        if (!DeltaEvents.hasAnyPermission(sender, "DeltaEvents.koth.admin", "DeltaEvents.admin")) {
            sender.sendMessage(kothManager.getMessage("koth.no_permission"));
            return true;
        }

        if (!kothManager.isActive() && !kothManager.isStarting()) {
            sender.sendMessage(kothManager.getMessage("koth.not_running"));
            return true;
        }

        kothManager.stop();
        sender.sendMessage(DeltaEvents.color(plugin.msg("koth.stop_success")));
        return true;
    }

    private boolean handleEditor(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(DeltaEvents.color(plugin.msg("koth.only_players")));
            return true;
        }

        if (!DeltaEvents.hasAnyPermission(player, "DeltaEvents.koth.admin", "DeltaEvents.admin")) {
            player.sendMessage(kothManager.getMessage("koth.no_permission"));
            return true;
        }

        String region = "arena";
        if (args.length >= 2) {
            String opt = args[1].toLowerCase();
            if (opt.equals("spawn") || opt.equals("arena")) {
                region = opt;
            } else {
                player.sendMessage(DeltaEvents.color("&cИзползване: /koth editor [arena|spawn]"));
                return true;
            }
        }

        ItemStack editorItem = kothManager.createEditorItem(region);
        player.getInventory().addItem(editorItem);
        player.sendMessage(DeltaEvents.color(plugin.msg("koth.editor_give_success").replace("{region}", region)));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!DeltaEvents.hasAnyPermission(sender, "DeltaEvents.reload", "DeltaEvents.admin", "DeltaEvents.koth.admin")) {
            sender.sendMessage(kothManager.getMessage("koth.no_permission"));
            return true;
        }

        try {
            kothManager.reload();
            sender.sendMessage(DeltaEvents.color(plugin.msg("koth.reload_success")));
        } catch (Exception e) {
            sender.sendMessage(DeltaEvents.color(plugin.msg("koth.reload_error").replace("{reason}", e.getMessage())));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            if (DeltaEvents.hasAnyPermission(sender, "DeltaEvents.koth.admin", "DeltaEvents.admin")) {
                subs.add("start");
                subs.add("stop");
                subs.add("editor");
            }
            if (DeltaEvents.hasAnyPermission(sender, "DeltaEvents.reload", "DeltaEvents.admin", "DeltaEvents.koth.admin")) {
                subs.add("reload");
            }
            StringUtil.copyPartialMatches(args[0], subs, completions);
            Collections.sort(completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("editor")) {
            if (DeltaEvents.hasAnyPermission(sender, "DeltaEvents.koth.admin", "DeltaEvents.admin")) {
                List<String> options = Arrays.asList("arena", "spawn");
                StringUtil.copyPartialMatches(args[1], options, completions);
                Collections.sort(completions);
            }
        }
        return completions;
    }
}
