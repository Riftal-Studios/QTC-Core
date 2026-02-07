package com.riftalstudios.qtccore.resources;

import java.util.UUID;

/**
 * Tracks per-player state for a resource node.
 * Each player has independent HP, cooldowns, and visibility for each node.
 *
 * In the pure packet-based system, we no longer track entity IDs.
 * The visibility is managed by the NodeBlockManager viewer system.
 */
public class PlayerNodeState {

    private final UUID playerId;
    private final UUID nodeId;
    private int currentHp;
    private final int maxHp;
    private long lastHitTime;
    private long regenerateAtTime;

    // Whether this player can see the node (managed by NodeBlockManager)
    // This is a local cache - the source of truth is NodeBlockManager.isViewer()
    private boolean isViewer;

    public PlayerNodeState(UUID playerId, UUID nodeId, int maxHp) {
        this.playerId = playerId;
        this.nodeId = nodeId;
        this.currentHp = maxHp;
        this.maxHp = maxHp;
        this.lastHitTime = 0;
        this.regenerateAtTime = 0;
        this.isViewer = false;
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

    // ==================== Viewer State ====================

    /**
     * Check if this player is currently viewing the node.
     * Note: This is a local cache. The source of truth is NodeBlockManager.isViewer().
     */
    public boolean isViewer() {
        return isViewer;
    }

    /**
     * Set the viewer state (called by NodeDisplayManager when adding/removing viewer).
     */
    public void setViewer(boolean viewer) {
        this.isViewer = viewer;
    }

    // ==================== Legacy Compatibility ====================
    // These methods exist for backward compatibility during migration.
    // They do nothing in the pure packet-based system.

    /**
     * @deprecated Entity IDs are no longer used in the packet-based system.
     */
    @Deprecated
    public boolean isDisplaySpawned() {
        return isViewer;
    }

    /**
     * @deprecated Entity IDs are no longer used in the packet-based system.
     */
    @Deprecated
    public void setDisplaySpawned(boolean spawned) {
        this.isViewer = spawned;
    }
}
