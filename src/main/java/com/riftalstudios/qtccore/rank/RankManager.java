package com.riftalstudios.qtccore.rank;

import com.riftalstudios.qtccore.Manager;
import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.events.RankUpEvent;
import com.riftalstudios.qtccore.profile.PlayerProfile;
import com.riftalstudios.qtccore.profile.ProfileManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public class RankManager implements Manager {

    private final QTCCore plugin;
    private final ProfileManager profileManager;

    public RankManager(QTCCore plugin, ProfileManager profileManager) {
        this.plugin = plugin;
        this.profileManager = profileManager;
    }

    @Override
    public void enable() {
        plugin.getLogger().info("RankManager enabled");
    }

    @Override
    public void disable() {
        plugin.getLogger().info("RankManager disabled");
    }

    @Override
    public String getName() {
        return "RankManager";
    }

    /**
     * Recalculates a player's rank based on their rep_total.
     * If the rank changed upward, fires a RankUpEvent and broadcasts.
     */
    public void checkRankUpdate(UUID uuid) {
        PlayerProfile profile = profileManager.getCachedProfile(uuid);
        if (profile == null) return;

        Rank oldRank = profile.getCurrentRank();
        Rank newRank = Rank.fromRepTotal(profile.getRepTotal());

        if (newRank != oldRank) {
            profile.setCurrentRank(newRank);
            profileManager.saveProfile(profile);

            // Fire event on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                RankUpEvent event = new RankUpEvent(uuid, oldRank, newRank);
                Bukkit.getPluginManager().callEvent(event);

                // Only broadcast for rank increases (lower ordinal = higher rank)
                if (newRank.ordinal() < oldRank.ordinal()) {
                    Player player = Bukkit.getPlayer(uuid);
                    String name = player != null ? player.getName() : uuid.toString();
                    Bukkit.broadcastMessage(
                            plugin.getMessageConfig().getMessage("rank.rank-up-broadcast",
                                    java.util.Map.of(
                                            "player", name,
                                            "old_rank", oldRank.getDisplayName(),
                                            "new_rank", newRank.getDisplayName()
                                    ))
                    );
                }
            });
        }
    }

    /**
     * Force-recalculates rank for a player (admin command).
     */
    public Rank recalculate(UUID uuid) {
        PlayerProfile profile = profileManager.getCachedProfile(uuid);
        if (profile == null) return Rank.OUTCAST;

        Rank newRank = Rank.fromRepTotal(profile.getRepTotal());
        profile.setCurrentRank(newRank);
        profileManager.saveProfile(profile);
        return newRank;
    }
}
