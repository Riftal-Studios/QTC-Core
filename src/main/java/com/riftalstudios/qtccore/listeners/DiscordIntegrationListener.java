package com.riftalstudios.qtccore.listeners;

import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.api.DiscordAPIClient;
import io.papermc.paper.advancement.AdvancementDisplay;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Listens for Minecraft events and forwards them to the Discord bot API.
 * All API calls run asynchronously to avoid blocking the main server thread.
 */
public class DiscordIntegrationListener implements Listener {

    private final QTCCore plugin;
    private final DiscordAPIClient client;

    public DiscordIntegrationListener(QTCCore plugin, DiscordAPIClient client) {
        this.plugin = plugin;
        this.client = client;
    }

    /**
     * Fires when a player joins the server.
     * Awards 5 points once per calendar day via the Discord bot API.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        String playerUuid = event.getPlayer().getUniqueId().toString();
        String playerName = event.getPlayer().getName();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                client.notifyJoin(playerUuid, playerName).join();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to notify Discord API of join for "
                        + playerName + ": " + e.getMessage());
            }
        });
    }

    /**
     * Fires when a player completes an advancement.
     * Filters out recipe unlocks and root advancements, then notifies the API.
     * Challenges (special frame type) earn bonus points.
     */
    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        Advancement advancement = event.getAdvancement();
        String key = advancement.getKey().toString();

        // Filter out recipe advancements — hundreds of these fire and they're not real achievements
        if (key.startsWith("minecraft:recipes/")) {
            return;
        }

        // Filter out root advancements (tab openers like "minecraft:story/root")
        if (key.endsWith("/root")) {
            return;
        }

        // Determine if this is a challenge
        AdvancementDisplay display = advancement.getDisplay();
        if (display == null) {
            // No display data means it's a hidden/internal advancement — skip
            return;
        }

        boolean isChallenge = display.frame() == AdvancementDisplay.Frame.CHALLENGE;
        String playerUuid = event.getPlayer().getUniqueId().toString();
        String playerName = event.getPlayer().getName();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                client.notifyAchievement(playerUuid, playerName, key, isChallenge).join();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to notify Discord API of advancement for "
                        + playerName + ": " + e.getMessage());
            }
        });
    }
}
