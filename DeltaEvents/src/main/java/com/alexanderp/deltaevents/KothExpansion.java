package com.alexanderp.deltaevents;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class KothExpansion extends PlaceholderExpansion {
    private final DeltaEvents plugin;
    private final KothManager kothManager;

    public KothExpansion(DeltaEvents plugin, KothManager kothManager) {
        this.plugin = plugin;
        this.kothManager = kothManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "deltaevents";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getDescription().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // Keeps the expansion registered on server reload
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        if (params.equalsIgnoreCase("koth_cappers")) {
            return kothManager.getCurrentCappersString();
        }
        if (params.equalsIgnoreCase("koth_time_left")) {
            return kothManager.getRemainingTimeString();
        }
        if (params.equalsIgnoreCase("koth_schedule")) {
            return kothManager.getScheduleString();
        }
        return null;
    }
}
