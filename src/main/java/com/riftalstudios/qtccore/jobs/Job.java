package com.riftalstudios.qtccore.jobs;

import java.util.UUID;

public class Job {

    private long id;
    private UUID posterUuid;
    private UUID accepterUuid;
    private JobCategory category;
    private String description;
    private int pointsOffered;
    private JobStatus status;
    private long createdAt;
    private long acceptedAt;
    private long completedAt;
    private long verifiedAt;
    private UUID verifiedBy;

    public Job(UUID posterUuid, JobCategory category, String description, int pointsOffered) {
        this.posterUuid = posterUuid;
        this.category = category;
        this.description = description;
        this.pointsOffered = pointsOffered;
        this.status = JobStatus.OPEN;
        this.createdAt = System.currentTimeMillis();
    }

    public Job(long id, UUID posterUuid, UUID accepterUuid, JobCategory category, String description,
               int pointsOffered, JobStatus status, long createdAt, long acceptedAt,
               long completedAt, long verifiedAt, UUID verifiedBy) {
        this.id = id;
        this.posterUuid = posterUuid;
        this.accepterUuid = accepterUuid;
        this.category = category;
        this.description = description;
        this.pointsOffered = pointsOffered;
        this.status = status;
        this.createdAt = createdAt;
        this.acceptedAt = acceptedAt;
        this.completedAt = completedAt;
        this.verifiedAt = verifiedAt;
        this.verifiedBy = verifiedBy;
    }

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public UUID getPosterUuid() { return posterUuid; }
    public UUID getAccepterUuid() { return accepterUuid; }
    public void setAccepterUuid(UUID accepterUuid) { this.accepterUuid = accepterUuid; }

    public JobCategory getCategory() { return category; }
    public String getDescription() { return description; }
    public int getPointsOffered() { return pointsOffered; }

    public JobStatus getStatus() { return status; }
    public void setStatus(JobStatus status) { this.status = status; }

    public long getCreatedAt() { return createdAt; }
    public long getAcceptedAt() { return acceptedAt; }
    public void setAcceptedAt(long acceptedAt) { this.acceptedAt = acceptedAt; }

    public long getCompletedAt() { return completedAt; }
    public void setCompletedAt(long completedAt) { this.completedAt = completedAt; }

    public long getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(long verifiedAt) { this.verifiedAt = verifiedAt; }

    public UUID getVerifiedBy() { return verifiedBy; }
    public void setVerifiedBy(UUID verifiedBy) { this.verifiedBy = verifiedBy; }
}
