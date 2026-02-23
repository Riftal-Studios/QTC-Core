package com.riftalstudios.qtccore.reputation;

import com.riftalstudios.qtccore.Manager;
import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.events.ReputationChangeEvent;
import com.riftalstudios.qtccore.profile.PlayerProfile;
import com.riftalstudios.qtccore.profile.ProfileManager;
import com.riftalstudios.qtccore.rank.RankManager;
import com.riftalstudios.qtccore.storage.DatabaseManager;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class ReputationManager implements Manager {

    private final QTCCore plugin;
    private final ProfileManager profileManager;
    private final RankManager rankManager;
    private final ReputationStorage storage;

    public ReputationManager(QTCCore plugin, DatabaseManager database,
                             ProfileManager profileManager, RankManager rankManager) {
        this.plugin = plugin;
        this.profileManager = profileManager;
        this.rankManager = rankManager;
        this.storage = new ReputationStorage(plugin, database);
    }

    @Override
    public void enable() {
        plugin.getLogger().info("ReputationManager enabled");
    }

    @Override
    public void disable() {
        plugin.getLogger().info("ReputationManager disabled");
    }

    @Override
    public String getName() {
        return "ReputationManager";
    }

    /**
     * Adds reputation to a player. Updates cache synchronously, persists async.
     * Positive amounts increase both balance and total. Negative amounts only decrease balance.
     */
    public void addReputation(UUID uuid, long amount, String reason, String sourceType, String sourceId) {
        PlayerProfile profile = profileManager.getCachedProfile(uuid);
        if (profile == null) {
            // Load profile first, then retry
            profileManager.getOrLoadProfile(uuid).thenAccept(p -> {
                if (p != null) {
                    applyRepChange(p, amount, reason, sourceType, sourceId);
                }
            });
            return;
        }

        applyRepChange(profile, amount, reason, sourceType, sourceId);
    }

    /**
     * Removes reputation from a player (balance only, total is never decreased).
     */
    public void removeReputation(UUID uuid, long amount, String reason, String sourceType, String sourceId) {
        addReputation(uuid, -Math.abs(amount), reason, sourceType, sourceId);
    }

    private void applyRepChange(PlayerProfile profile, long amount, String reason,
                                String sourceType, String sourceId) {
        // Update balance
        long newBalance = profile.getRepBalance() + amount;
        if (newBalance < 0) newBalance = 0;
        profile.setRepBalance(newBalance);

        // Only increase total for positive amounts
        if (amount > 0) {
            profile.setRepTotal(profile.getRepTotal() + amount);
        }

        // Create ledger entry
        ReputationTransaction txn = new ReputationTransaction(
                0, profile.getUuid(), amount, reason, sourceType, sourceId,
                profile.getRepBalance(), profile.getRepTotal(), System.currentTimeMillis()
        );

        // Persist async
        storage.insertTransaction(txn);
        profileManager.saveProfile(profile);

        // Fire event on main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            ReputationChangeEvent event = new ReputationChangeEvent(
                    profile.getUuid(), amount, reason,
                    profile.getRepBalance(), profile.getRepTotal()
            );
            Bukkit.getPluginManager().callEvent(event);
        });

        // Check for rank change
        rankManager.checkRankUpdate(profile.getUuid());
    }

    public CompletableFuture<List<ReputationTransaction>> getHistory(UUID uuid, int page) {
        return storage.getHistory(uuid, page, 10);
    }

    public ReputationStorage getStorage() {
        return storage;
    }
}
