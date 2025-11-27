package com.riftalstudios.qtccore.resources;

import org.bukkit.Location;
import java.util.UUID;

public class ResourceNode {

    private final UUID id;
    private final Location location;
    private final ResourceType type;
    private final int maxHp;
    private int currentHp;
    private NodeState state;
    private long depletedAt;

    public ResourceNode(Location location, ResourceType type) {
        this(UUID.randomUUID(), location, type, type.getDefaultMaxHp());
    }

    public ResourceNode(UUID id, Location location, ResourceType type, int maxHp) {
        this.id = id;
        this.location = location;
        this.type = type;
        this.maxHp = maxHp;
        this.currentHp = maxHp;
        this.state = NodeState.ACTIVE;
        this.depletedAt = 0;
    }

    public UUID getId() {
        return id;
    }

    public Location getLocation() {
        return location;
    }

    public ResourceType getType() {
        return type;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public int getCurrentHp() {
        return currentHp;
    }

    public void setCurrentHp(int hp) {
        this.currentHp = Math.max(0, Math.min(hp, maxHp));
        if (this.currentHp == 0 && state == NodeState.ACTIVE) {
            deplete();
        }
    }

    public NodeState getState() {
        return state;
    }

    public void setState(NodeState state) {
        this.state = state;
    }

    public long getDepletedAt() {
        return depletedAt;
    }

    public boolean damage(int amount) {
        if (state != NodeState.ACTIVE) return false;
        int previousHp = currentHp;
        setCurrentHp(currentHp - amount);
        return previousHp > 0 && currentHp == 0;
    }

    public void deplete() {
        this.state = NodeState.DEPLETED;
        this.currentHp = 0;
        this.depletedAt = System.currentTimeMillis();
    }

    public void regenerate() {
        this.state = NodeState.ACTIVE;
        this.currentHp = maxHp;
        this.depletedAt = 0;
    }

    public boolean isDepleted() {
        return state == NodeState.DEPLETED || state == NodeState.REGENERATING;
    }

    public int getChunkX() {
        return location.getBlockX() >> 4;
    }

    public int getChunkZ() {
        return location.getBlockZ() >> 4;
    }

    public String getWorldName() {
        return location.getWorld() != null ? location.getWorld().getName() : "unknown";
    }
}
