package com.riftalstudios.qtccore.profile;

import com.riftalstudios.qtccore.rank.Rank;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PlayerProfile {

    private final UUID uuid;
    private String discordId;
    private String roleIcon;
    private List<Badge> selectedBadges;
    private List<ServiceIdentity> selectedIdentities;
    private long repBalance;
    private long repTotal;
    private long taskPointBalance;
    private long taskPointTotal;
    private Rank currentRank;
    private boolean staff;
    private long lastActive;
    private long createdAt;

    public PlayerProfile(UUID uuid) {
        this.uuid = uuid;
        this.selectedBadges = new ArrayList<>();
        this.selectedIdentities = new ArrayList<>();
        this.repBalance = 0;
        this.repTotal = 0;
        this.taskPointBalance = 0;
        this.taskPointTotal = 0;
        this.currentRank = Rank.OUTCAST;
        this.staff = false;
        this.lastActive = System.currentTimeMillis();
        this.createdAt = System.currentTimeMillis();
    }

    public UUID getUuid() { return uuid; }

    public String getDiscordId() { return discordId; }
    public void setDiscordId(String discordId) { this.discordId = discordId; }

    public String getRoleIcon() { return roleIcon; }
    public void setRoleIcon(String roleIcon) { this.roleIcon = roleIcon; }

    public List<Badge> getSelectedBadges() { return selectedBadges; }
    public void setSelectedBadges(List<Badge> selectedBadges) { this.selectedBadges = selectedBadges; }

    public List<ServiceIdentity> getSelectedIdentities() { return selectedIdentities; }
    public void setSelectedIdentities(List<ServiceIdentity> selectedIdentities) { this.selectedIdentities = selectedIdentities; }

    public long getRepBalance() { return repBalance; }
    public void setRepBalance(long repBalance) { this.repBalance = repBalance; }

    public long getRepTotal() { return repTotal; }
    public void setRepTotal(long repTotal) { this.repTotal = repTotal; }

    public long getTaskPointBalance() { return taskPointBalance; }
    public void setTaskPointBalance(long taskPointBalance) { this.taskPointBalance = taskPointBalance; }

    public long getTaskPointTotal() { return taskPointTotal; }
    public void setTaskPointTotal(long taskPointTotal) { this.taskPointTotal = taskPointTotal; }

    public Rank getCurrentRank() { return currentRank; }
    public void setCurrentRank(Rank currentRank) { this.currentRank = currentRank; }

    public boolean isStaff() { return staff; }
    public void setStaff(boolean staff) { this.staff = staff; }

    public long getLastActive() { return lastActive; }
    public void setLastActive(long lastActive) { this.lastActive = lastActive; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
