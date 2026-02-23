package com.riftalstudios.qtccore.listeners;

import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.profile.PlayerProfile;
import com.riftalstudios.qtccore.profile.ProfileManager;
import com.riftalstudios.qtccore.reputation.ReputationManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.concurrent.TimeUnit;

public class ProfileListener implements Listener {

    private final QTCCore plugin;
    private final ProfileManager profileManager;
    private final ReputationManager reputationManager;

    public ProfileListener(QTCCore plugin, ProfileManager profileManager, ReputationManager reputationManager) {
        this.plugin = plugin;
        this.profileManager = profileManager;
        this.reputationManager = reputationManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        var uuid = player.getUniqueId();

        profileManager.getOrLoadProfile(uuid).thenAccept(profile -> {
            if (profile == null) return;

            long now = System.currentTimeMillis();

            // Check for daily login reputation bonus
            int dailyBonus = plugin.getConfigManager().getInt("reputation.daily-login-bonus");
            if (dailyBonus > 0) {
                long lastActive = profile.getLastActive();
                long daysSinceLast = TimeUnit.MILLISECONDS.toDays(now - lastActive);

                if (daysSinceLast >= 1) {
                    reputationManager.addReputation(
                            uuid, dailyBonus, "Daily login bonus",
                            "DAILY_LOGIN", null
                    );
                }
            }

            // Update last active
            profile.setLastActive(now);
            profileManager.saveProfile(profile);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        var uuid = event.getPlayer().getUniqueId();
        var profile = profileManager.getCachedProfile(uuid);

        if (profile != null) {
            profile.setLastActive(System.currentTimeMillis());
            profileManager.saveProfile(profile).thenRun(() -> profileManager.evict(uuid));
        }
    }
}
