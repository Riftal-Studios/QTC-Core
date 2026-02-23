package com.riftalstudios.qtccore.events;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class ReputationChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID playerUuid;
    private final long amount;
    private final String reason;
    private final long newBalance;
    private final long newTotal;

    public ReputationChangeEvent(UUID playerUuid, long amount, String reason, long newBalance, long newTotal) {
        this.playerUuid = playerUuid;
        this.amount = amount;
        this.reason = reason;
        this.newBalance = newBalance;
        this.newTotal = newTotal;
    }

    public UUID getPlayerUuid() { return playerUuid; }
    public long getAmount() { return amount; }
    public String getReason() { return reason; }
    public long getNewBalance() { return newBalance; }
    public long getNewTotal() { return newTotal; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
