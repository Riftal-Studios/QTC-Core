package com.riftalstudios.qtccore.events;

import com.riftalstudios.qtccore.rank.Rank;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class RankUpEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerUuid;
    private final Rank oldRank;
    private final Rank newRank;

    public RankUpEvent(UUID playerUuid, Rank oldRank, Rank newRank) {
        this.playerUuid = playerUuid;
        this.oldRank = oldRank;
        this.newRank = newRank;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public Rank getOldRank() { return oldRank; }
    public Rank getNewRank() { return newRank; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
