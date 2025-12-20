package com.riftalstudios.qtccore.resources;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tracks per-player state for a resource node.
 * Each player has independent HP, cooldowns, and display entities for each node.
 * Supports multi-block nodes with multiple display/interaction entity pairs.
 */
public class PlayerNodeState {

    private final UUID playerId;
    private final UUID nodeId;
    private int currentHp;
    private final int maxHp;
    private long lastHitTime;
    private long regenerateAtTime;
    private boolean displaySpawned;

    // Multi-block support: one entity pair per block
    private final List<Integer> displayEntityIds;
    private final List<Integer> interactionEntityIds;

    // Legacy single-block access (for backwards compatibility)
    private int displayEntityId;
    private int interactionEntityId;

    public PlayerNodeState(UUID playerId, UUID nodeId, int maxHp) {
        this.playerId = playerId;
        this.nodeId = nodeId;
        this.currentHp = maxHp;
        this.maxHp = maxHp;
        this.lastHitTime = 0;
        this.regenerateAtTime = 0;
        this.displayEntityId = -1;
        this.interactionEntityId = -1;
        this.displaySpawned = false;
        this.displayEntityIds = new ArrayList<>();
        this.interactionEntityIds = new ArrayList<>();
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public UUID getNodeId() {
        return nodeId;
    }

    public int getCurrentHp() {
        return currentHp;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public void setCurrentHp(int hp) {
        this.currentHp = Math.max(0, Math.min(hp, maxHp));
    }

    public void damage(int amount) {
        this.currentHp = Math.max(0, currentHp - amount);
        this.lastHitTime = System.currentTimeMillis();
    }

    public boolean isDepleted() {
        return currentHp <= 0;
    }

    public void resetHp() {
        this.currentHp = maxHp;
        this.regenerateAtTime = 0;
    }

    public long getLastHitTime() {
        return lastHitTime;
    }

    public void setLastHitTime(long time) {
        this.lastHitTime = time;
    }

    public boolean isOnCooldown(long cooldownMs) {
        return System.currentTimeMillis() - lastHitTime < cooldownMs;
    }

    public long getRegenerateAtTime() {
        return regenerateAtTime;
    }

    public void setRegenerateAtTime(long time) {
        this.regenerateAtTime = time;
    }

    public boolean shouldRegenerate() {
        return regenerateAtTime > 0 && System.currentTimeMillis() >= regenerateAtTime;
    }

    public void scheduleRegeneration(long delayMs) {
        this.regenerateAtTime = System.currentTimeMillis() + delayMs;
    }

    // ==================== Legacy Single-Block Access ====================

    public int getDisplayEntityId() {
        return displayEntityId;
    }

    public void setDisplayEntityId(int entityId) {
        this.displayEntityId = entityId;
    }

    public int getInteractionEntityId() {
        return interactionEntityId;
    }

    public void setInteractionEntityId(int entityId) {
        this.interactionEntityId = entityId;
    }

    // ==================== Multi-Block Entity Access ====================

    /**
     * Get all display entity IDs for this node.
     */
    public List<Integer> getDisplayEntityIds() {
        return displayEntityIds;
    }

    /**
     * Get all interaction entity IDs for this node.
     */
    public List<Integer> getInteractionEntityIds() {
        return interactionEntityIds;
    }

    /**
     * Add a display entity ID.
     */
    public void addDisplayEntityId(int entityId) {
        displayEntityIds.add(entityId);
        // Keep legacy field updated with first ID
        if (displayEntityId == -1) {
            displayEntityId = entityId;
        }
    }

    /**
     * Add an interaction entity ID.
     */
    public void addInteractionEntityId(int entityId) {
        interactionEntityIds.add(entityId);
        // Keep legacy field updated with first ID
        if (interactionEntityId == -1) {
            interactionEntityId = entityId;
        }
    }

    /**
     * Get all entity IDs (display + interaction) as a flat array.
     */
    public int[] getAllEntityIds() {
        int[] ids = new int[displayEntityIds.size() + interactionEntityIds.size()];
        int i = 0;
        for (int id : displayEntityIds) {
            ids[i++] = id;
        }
        for (int id : interactionEntityIds) {
            ids[i++] = id;
        }
        return ids;
    }

    /**
     * Clear all entity IDs (on hide).
     */
    public void clearEntityIds() {
        displayEntityIds.clear();
        interactionEntityIds.clear();
        displayEntityId = -1;
        interactionEntityId = -1;
    }

    // ==================== Display State ====================

    public boolean isDisplaySpawned() {
        return displaySpawned;
    }

    public void setDisplaySpawned(boolean spawned) {
        this.displaySpawned = spawned;
    }
}
