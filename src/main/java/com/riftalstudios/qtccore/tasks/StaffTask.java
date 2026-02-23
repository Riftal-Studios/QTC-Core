package com.riftalstudios.qtccore.tasks;

import java.util.UUID;

public class StaffTask {

    private long id;
    private String title;
    private String description;
    private UUID creatorUuid;
    private UUID assigneeUuid;
    private int suggestedPoints;
    private int approvedPoints;
    private TaskStatus status;
    private UUID approvedBy;
    private long createdAt;
    private long assignedAt;
    private long submittedAt;
    private long resolvedAt;

    public StaffTask(String title, String description, UUID creatorUuid, UUID assigneeUuid, int suggestedPoints) {
        this.title = title;
        this.description = description;
        this.creatorUuid = creatorUuid;
        this.assigneeUuid = assigneeUuid;
        this.suggestedPoints = suggestedPoints;
        this.approvedPoints = 0;
        this.status = assigneeUuid != null ? TaskStatus.ASSIGNED : TaskStatus.CREATED;
        this.createdAt = System.currentTimeMillis();
        if (assigneeUuid != null) {
            this.assignedAt = this.createdAt;
        }
    }

    public StaffTask(long id, String title, String description, UUID creatorUuid, UUID assigneeUuid,
                     int suggestedPoints, int approvedPoints, TaskStatus status, UUID approvedBy,
                     long createdAt, long assignedAt, long submittedAt, long resolvedAt) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.creatorUuid = creatorUuid;
        this.assigneeUuid = assigneeUuid;
        this.suggestedPoints = suggestedPoints;
        this.approvedPoints = approvedPoints;
        this.status = status;
        this.approvedBy = approvedBy;
        this.createdAt = createdAt;
        this.assignedAt = assignedAt;
        this.submittedAt = submittedAt;
        this.resolvedAt = resolvedAt;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getTitle() { return title; }
    public String getDescription() { return description; }

    public UUID getCreatorUuid() { return creatorUuid; }
    public UUID getAssigneeUuid() { return assigneeUuid; }
    public void setAssigneeUuid(UUID assigneeUuid) { this.assigneeUuid = assigneeUuid; }

    public int getSuggestedPoints() { return suggestedPoints; }
    public int getApprovedPoints() { return approvedPoints; }
    public void setApprovedPoints(int approvedPoints) { this.approvedPoints = approvedPoints; }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }

    public UUID getApprovedBy() { return approvedBy; }
    public void setApprovedBy(UUID approvedBy) { this.approvedBy = approvedBy; }

    public long getCreatedAt() { return createdAt; }
    public long getAssignedAt() { return assignedAt; }
    public void setAssignedAt(long assignedAt) { this.assignedAt = assignedAt; }

    public long getSubmittedAt() { return submittedAt; }
    public void setSubmittedAt(long submittedAt) { this.submittedAt = submittedAt; }

    public long getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(long resolvedAt) { this.resolvedAt = resolvedAt; }
}
