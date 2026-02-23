package com.riftalstudios.qtccore.profile;

import com.riftalstudios.qtccore.Manager;
import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.storage.DatabaseManager;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class ProfileManager implements Manager {

    private final QTCCore plugin;
    private final ProfileStorage storage;
    private final ConcurrentHashMap<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();

    public ProfileManager(QTCCore plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.storage = new ProfileStorage(plugin, database);
    }

    @Override
    public void enable() {
        plugin.getLogger().info("ProfileManager enabled");
    }

    @Override
    public void disable() {
        // Save all cached profiles on shutdown
        for (PlayerProfile profile : cache.values()) {
            storage.saveProfile(profile).join();
        }
        cache.clear();
        plugin.getLogger().info("ProfileManager disabled - all profiles saved");
    }

    @Override
    public String getName() {
        return "ProfileManager";
    }

    /**
     * Loads a profile from DB, or creates a new one if none exists.
     * Caches the result in memory.
     */
    public CompletableFuture<PlayerProfile> getOrLoadProfile(UUID uuid) {
        PlayerProfile cached = cache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        return storage.loadProfile(uuid).thenApply(profile -> {
            if (profile == null) {
                profile = new PlayerProfile(uuid);
                storage.saveProfile(profile);
            }
            cache.put(uuid, profile);
            return profile;
        });
    }

    /**
     * Returns the cached profile for an online player (null if not cached).
     */
    public PlayerProfile getCachedProfile(UUID uuid) {
        return cache.get(uuid);
    }

    /**
     * Saves the profile asynchronously and keeps it in cache.
     */
    public CompletableFuture<Void> saveProfile(PlayerProfile profile) {
        return storage.saveProfile(profile);
    }

    /**
     * Removes the profile from cache (call on player quit after saving).
     */
    public void evict(UUID uuid) {
        cache.remove(uuid);
    }

    public ProfileStorage getStorage() {
        return storage;
    }
}
