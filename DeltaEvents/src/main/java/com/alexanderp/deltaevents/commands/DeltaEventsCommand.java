package com.alexanderp.deltaevents.commands;

import com.alexanderp.deltaevents.DeltaEvents;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

public class DeltaEventsCommand implements CommandExecutor, TabCompleter {
    private final DeltaEvents plugin;

    public DeltaEventsCommand(DeltaEvents plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);

        if (commandName.equals("deltaevents")) {
            return handleDeltaEvents(sender, args);
        }

        return false;
    }

    private boolean handleDeltaEvents(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sender.sendMessage(DeltaEvents.color(plugin.msg("help_header")
                .replace("{name}", plugin.getDescription().getName())
                .replace("{version}", plugin.getDescription().getVersion())
                .replace("{author}", String.join(", ", plugin.getDescription().getAuthors()))));
            sender.sendMessage(DeltaEvents.color(plugin.msg("help_commands")));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!DeltaEvents.hasAnyPermission(sender, "DeltaEvents.reload", "DeltaEvents.admin")) {
                sender.sendMessage(DeltaEvents.color(plugin.msg("no_permission")));
                return true;
            }

            try {
                plugin.reloadAll();
                sender.sendMessage(DeltaEvents.color(plugin.msg("reload_ok")
                    .replace("{file}", "всички файлове (config.yml, lang.yml)")));
            } catch (Exception e) {
                sender.sendMessage(DeltaEvents.color(plugin.msg("reload_fail")
                    .replace("{file}", "конфигурация")
                    .replace("{reason}", e.getMessage())));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(DeltaEvents.color("&a&lДобавени събития (Events):"));
            sender.sendMessage(DeltaEvents.color(" &8• &fSumo"));
            sender.sendMessage(DeltaEvents.color(" &8• &fKOTH"));
            sender.sendMessage(DeltaEvents.color(" &8• &fMrBeast"));
            sender.sendMessage(DeltaEvents.color(" &8• &fFiveM"));
            return true;
        }

        sender.sendMessage(DeltaEvents.color(plugin.msg("deltaevents_usage")));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String commandName = command.getName().toLowerCase(Locale.ROOT);
        List<String> completions = new ArrayList<>();

        if (commandName.equals("deltaevents")) {
            if (args.length == 1) {
                List<String> subCommands = new ArrayList<>();
                subCommands.add("help");
                subCommands.add("list");
                if (DeltaEvents.hasAnyPermission(sender, "DeltaEvents.reload", "DeltaEvents.admin")) {
                    subCommands.add("reload");
                }
                StringUtil.copyPartialMatches(args[0], subCommands, completions);
            }
        }

        return completions;
    }
}
