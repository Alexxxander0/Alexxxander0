package com.alexanderp.deltaevents.commands;

import com.alexanderp.deltaevents.DeltaEvents;
import com.alexanderp.deltaevents.SumoManager;
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

public class SumoCommand implements CommandExecutor, TabCompleter {
    private final DeltaEvents plugin;
    private final SumoManager sumoManager;

    public SumoCommand(DeltaEvents plugin, SumoManager sumoManager) {
        this.plugin = plugin;
        this.sumoManager = sumoManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "rules":
                return handleRules(sender);
            case "start":
                return handleStart(sender);
            case "stop":
                return handleStop(sender);
            case "round":
                return handleRound(sender, args);
            case "giveitems":
                return handleGetItems(sender);
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
        sender.sendMessage(DeltaEvents.color(plugin.msg("sumo.usage_header")));
        sender.sendMessage(DeltaEvents.color(plugin.msg("sumo.usage_title")));
        sender.sendMessage(DeltaEvents.color(plugin.msg("sumo.usage_rules")));
        if (DeltaEvents.hasAnyPermission(sender, "DeltaEvents.sumo.admin", "DeltaEvents.admin")) {
            sender.sendMessage(DeltaEvents.color(plugin.msg("sumo.usage_start")));
            sender.sendMessage(DeltaEvents.color(plugin.msg("sumo.usage_round")));
            sender.sendMessage(DeltaEvents.color(plugin.msg("sumo.usage_giveitems")));
            sender.sendMessage(DeltaEvents.color(plugin.msg("sumo.usage_editor")));
            sender.sendMessage(DeltaEvents.color(plugin.msg("sumo.usage_stop")));
            sender.sendMessage(DeltaEvents.color(plugin.msg("sumo.usage_reload")));
        }
        sender.sendMessage(DeltaEvents.color(plugin.msg("sumo.usage_footer")));
    }

    private boolean handleRules(CommandSender sender) {
        if (!DeltaEvents.hasAnyPermission(sender, "DeltaEvents.sumo.rules", "DeltaEvents.sumo.admin", "DeltaEvents.admin")) {
            sender.sendMessage(sumoManager.getMessage("sumo.no_permission"));
            return true;
        }

        List<String> rules = plugin.getLang().getStringList("sumo.rules");
        String worldName = plugin.getEventWorldName();

        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world != null) {
            for (String rLine : rules) {
                String colored = DeltaEvents.color(rLine);
                for (Player p : world.getPlayers()) {
                    p.sendMessage(colored);
                }
            }
        }

        sumoManager.broadcastSound("rules");

        if (sender instanceof Player pSender) {
            if (!pSender.getWorld().getName().equalsIgnoreCase(worldName)) {
                sender.sendMessage(DeltaEvents.color(plugin.msg("sumo.rules_sent").replace("{world}", worldName)));
                for (String rLine : rules) {
                    pSender.sendMessage(DeltaEvents.color(rLine));
                }
                plugin.playSound(pSender, sumoManager.getConfig().getString("sounds.rules", "rules"), 1.0f, 1.0f);
            }
        } else {
            sender.sendMessage(DeltaEvents.color(plugin.msg("sumo.rules_sent_console").replace("{world}", worldName)));
        }

        return true;
    }

    private boolean handleStart(CommandSender sender) {
        if (!DeltaEvents.hasAnyPermission(sender, "DeltaEvents.sumo.admin", "DeltaEvents.admin")) {
            sender.sendMessage(sumoManager.getMessage("sumo.no_permission"));
            return true;
        }

        if (sumoManager.isActive() || sumoManager.isStarting()) {
            sender.sendMessage(sumoManager.getMessage("sumo.already_running"));
            return true;
        }

        if (sender instanceof Player player) {
            sumoManager.startCountdown(player);
        } else {
            sumoManager.startCountdown(null);
            sender.sendMessage(DeltaEvents.color(plugin.msg("sumo.countdown_started")));
        }
        return true;
    }

    private boolean handleStop(CommandSender sender) {
        if (!DeltaEvents.hasAnyPermission(sender, "DeltaEvents.sumo.admin", "DeltaEvents.admin")) {
            sender.sendMessage(sumoManager.getMessage("sumo.no_permission"));
            return true;
        }

        if (!sumoManager.isActive() && !sumoManager.isStarting()) {
            sender.sendMessage(sumoManager.getMessage("sumo.not_running"));
            return true;
        }

        sumoManager.stop();
        sender.sendMessage(DeltaEvents.color(plugin.msg("sumo.stop_success")));
        return true;
    }

    private boolean handleRound(CommandSender sender, String[] args) {
        if (!DeltaEvents.hasAnyPermission(sender, "DeltaEvents.sumo.admin", "DeltaEvents.admin")) {
            sender.sendMessage(sumoManager.getMessage("sumo.no_permission"));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(sumoManager.getMessage("sumo.invalid_round"));
            return true;
        }

        try {
            int round = Integer.parseInt(args[1]);
            sumoManager.startRound(round);

            String msg = DeltaEvents.color(plugin.msg("sumo.round_started").replace("{round}", String.valueOf(round)));
            for (Player p : org.bukkit.Bukkit.getOnlinePlayers()) {
                if (DeltaEvents.hasAnyPermission(p, "DeltaEvents.sumo.admin", "DeltaEvents.admin")) {
                    p.sendMessage(msg);
                }
            }
            org.bukkit.Bukkit.getConsoleSender().sendMessage(msg);
        } catch (NumberFormatException e) {
            sender.sendMessage(sumoManager.getMessage("sumo.invalid_round"));
        }
        return true;
    }

    private boolean handleGetItems(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(DeltaEvents.color(plugin.msg("sumo.only_players")));
            return true;
        }

        if (!DeltaEvents.hasAnyPermission(player, "DeltaEvents.sumo.admin", "DeltaEvents.admin")) {
            player.sendMessage(sumoManager.getMessage("sumo.no_permission"));
            return true;
        }

        ItemStack stick = sumoManager.createStick();
        player.getInventory().addItem(stick);
        player.sendMessage(DeltaEvents.color(plugin.msg("sumo.getitems_success")));
        return true;
    }

    private boolean handleEditor(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(DeltaEvents.color(plugin.msg("sumo.only_players")));
            return true;
        }

        if (!DeltaEvents.hasAnyPermission(player, "DeltaEvents.sumo.admin", "DeltaEvents.admin")) {
            player.sendMessage(sumoManager.getMessage("sumo.no_permission"));
            return true;
        }

        String region = "participant";
        if (args.length >= 2) {
            String opt = args[1].toLowerCase();
            if (opt.equals("spawn") || opt.equals("participant") || opt.equals("floor")) {
                region = opt;
            } else {
                player.sendMessage(DeltaEvents.color("&cИзползване: /sumo editor [participant|spawn|floor]"));
                return true;
            }
        }

        ItemStack editorItem = sumoManager.createEditorItem(region);
        player.getInventory().addItem(editorItem);
        player.sendMessage(DeltaEvents.color(plugin.msg("sumo.editor_give_success").replace("{region}", region)));
        return true;
    }

    private boolean handleReload(CommandSender sender) {
        if (!DeltaEvents.hasAnyPermission(sender, "DeltaEvents.reload", "DeltaEvents.admin", "DeltaEvents.sumo.admin")) {
            sender.sendMessage(sumoManager.getMessage("sumo.no_permission"));
            return true;
        }

        try {
            sumoManager.reload();
            sender.sendMessage(DeltaEvents.color(plugin.msg("sumo.reload_success")));
        } catch (Exception e) {
            sender.sendMessage(DeltaEvents.color(plugin.msg("sumo.reload_error").replace("{reason}", e.getMessage())));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = new ArrayList<>();
            if (DeltaEvents.hasAnyPermission(sender, "DeltaEvents.sumo.rules", "DeltaEvents.sumo.admin", "DeltaEvents.admin")) {
                subs.add("rules");
            }
            if (DeltaEvents.hasAnyPermission(sender, "DeltaEvents.sumo.admin", "DeltaEvents.admin")) {
                subs.add("start");
                subs.add("stop");
                subs.add("round");
                subs.add("giveitems");
                subs.add("editor");
            }
            if (DeltaEvents.hasAnyPermission(sender, "DeltaEvents.reload", "DeltaEvents.admin", "DeltaEvents.sumo.admin")) {
                subs.add("reload");
            }
            StringUtil.copyPartialMatches(args[0], subs, completions);
            Collections.sort(completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("round")) {
            if (DeltaEvents.hasAnyPermission(sender, "DeltaEvents.sumo.admin", "DeltaEvents.admin")) {
                completions.addAll(Arrays.asList("1", "2", "3", "4", "5"));
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("editor")) {
            if (DeltaEvents.hasAnyPermission(sender, "DeltaEvents.sumo.admin", "DeltaEvents.admin")) {
                List<String> options = Arrays.asList("participant", "spawn", "floor");
                StringUtil.copyPartialMatches(args[1], options, completions);
                Collections.sort(completions);
            }
        }
        return completions;
    }
}
