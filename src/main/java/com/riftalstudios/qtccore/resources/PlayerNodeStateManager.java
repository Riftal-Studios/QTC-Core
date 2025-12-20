package com.riftalstudios.qtccore.resources;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages per-player state for all resource nodes.
 * Structure: playerId -> nodeId -> PlayerNodeState
 */
public class PlayerNodeStateManager {

    // playerId -> (nodeId -> state)
    private final Map<UUID, Map<UUID, PlayerNodeState>> playerStates = new ConcurrentHashMap<>();

    /**
     * Get or create state for a player-node pair.
     */
    public PlayerNodeState getOrCreateState(UUID playerId, ResourceNode node) {
        Map<UUID, PlayerNodeState> nodeStates = playerStates.computeIfAbsent(
                playerId, k -> new ConcurrentHashMap<>()
        );

        return nodeStates.computeIfAbsent(node.getId(), k -> {
            PlayerNodeState state = new PlayerNodeState(playerId, node.getId(), node.getMaxHp());
            state.setDisplayEntityId(generateEntityId());
            state.setInteractionEntityId(generateEntityId());
            return state;
        });
    }

    /**
     * Get existing state, or null if none exists.
     */
    public PlayerNodeState getState(UUID playerId, UUID nodeId) {
        Map<UUID, PlayerNodeState> nodeStates = playerStates.get(playerId);
        if (nodeStates == null) return null;
        return nodeStates.get(nodeId);
    }

    /**
     * Remove all states for a player (on logout).
     */
    public Map<UUID, PlayerNodeState> removePlayer(UUID playerId) {
        return playerStates.remove(playerId);
    }

    /**
     * Remove state for a specific node from all players (on node removal).
     */
    public void removeNode(UUID nodeId) {
        for (Map<UUID, PlayerNodeState> nodeStates : playerStates.values()) {
            nodeStates.remove(nodeId);
        }
    }

    /**
     * Get all states for a player.
     */
    public Map<UUID, PlayerNodeState> getPlayerStates(UUID playerId) {
        return playerStates.getOrDefault(playerId, Map.of());
    }

    /**
     * Get all player states for a specific node.
     */
    public Map<UUID, PlayerNodeState> getNodeStates(UUID nodeId) {
        Map<UUID, PlayerNodeState> result = new ConcurrentHashMap<>();
        for (Map.Entry<UUID, Map<UUID, PlayerNodeState>> entry : playerStates.entrySet()) {
            PlayerNodeState state = entry.getValue().get(nodeId);
            if (state != null) {
                result.put(entry.getKey(), state);
            }
        }
        return result;
    }

    /**
     * Clear all state data.
     */
    public void clear() {
        playerStates.clear();
    }

    /**
     * Generate a unique entity ID for client-side display.
     * Uses high range to avoid collision with real entities.
     */
    private int generateEntityId() {
        return ThreadLocalRandom.current().nextInt(100000, Integer.MAX_VALUE);
    }

    /**
     * Get count of active player states (for debugging).
     */
    public int getActivePlayerCount() {
        return playerStates.size();
    }

    /**
     * Get total state count across all players (for debugging).
     */
    public int getTotalStateCount() {
        return playerStates.values().stream()
                .mapToInt(Map::size)
                .sum();
    }
}
