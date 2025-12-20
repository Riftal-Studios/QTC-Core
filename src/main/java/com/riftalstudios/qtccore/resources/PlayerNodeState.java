package com.riftalstudios.qtccore.resources;

import java.util.UUID;

/**
 * Tracks per-player state for a resource node.
 * Each player has independent HP, cooldowns, and display entity for each node.
 */
public class PlayerNodeState {

    private final UUID playerId;
    private final UUID nodeId;
    private int currentHp;
    private final int maxHp;
    private long lastHitTime;
    private long regenerateAtTime;
    private int displayEntityId;
    private int interactionEntityId;
    private boolean displaySpawned;

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

    public boolean isDisplaySpawned() {
        return displaySpawned;
    }

    public void setDisplaySpawned(boolean spawned) {
        this.displaySpawned = spawned;
    }
}
