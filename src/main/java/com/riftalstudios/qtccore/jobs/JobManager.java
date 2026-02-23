package com.riftalstudios.qtccore.jobs;

import com.riftalstudios.qtccore.Manager;
import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.events.JobCompletedEvent;
import com.riftalstudios.qtccore.reputation.ReputationManager;
import com.riftalstudios.qtccore.storage.DatabaseManager;
import org.bukkit.Bukkit;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class JobManager implements Manager {

    private final QTCCore plugin;
    private final JobStorage storage;
    private final ReputationManager reputationManager;

    public JobManager(QTCCore plugin, DatabaseManager database, ReputationManager reputationManager) {
        this.plugin = plugin;
        this.storage = new JobStorage(plugin, database);
        this.reputationManager = reputationManager;
    }

    @Override
    public void enable() {
        plugin.getLogger().info("JobManager enabled");
    }

    @Override
    public void disable() {
        plugin.getLogger().info("JobManager disabled");
    }

    @Override
    public String getName() {
        return "JobManager";
    }

    /**
     * Posts a new job. Validates points are within the category's max.
     */
    public CompletableFuture<Job> postJob(UUID posterUuid, JobCategory category, String description, int points) {
        if (points > category.getMaxPoints()) {
            points = category.getMaxPoints();
        }
        if (points < 1) {
            points = 1;
        }

        Job job = new Job(posterUuid, category, description, points);
        return storage.insertJob(job).thenApply(id -> job);
    }

    /**
     * A player accepts an open job.
     */
    public CompletableFuture<Boolean> acceptJob(long jobId, UUID accepterUuid) {
        return storage.getJob(jobId).thenCompose(job -> {
            if (job == null || job.getStatus() != JobStatus.OPEN) {
                return CompletableFuture.completedFuture(false);
            }
            if (job.getPosterUuid().equals(accepterUuid)) {
                return CompletableFuture.completedFuture(false);
            }

            job.setAccepterUuid(accepterUuid);
            job.setStatus(JobStatus.ACCEPTED);
            job.setAcceptedAt(System.currentTimeMillis());

            return storage.updateJob(job).thenApply(v -> true);
        });
    }

    /**
     * The accepter marks a job as completed.
     */
    public CompletableFuture<Boolean> completeJob(long jobId, UUID requesterUuid) {
        return storage.getJob(jobId).thenCompose(job -> {
            if (job == null || job.getStatus() != JobStatus.ACCEPTED) {
                return CompletableFuture.completedFuture(false);
            }
            if (!job.getAccepterUuid().equals(requesterUuid)) {
                return CompletableFuture.completedFuture(false);
            }

            job.setStatus(JobStatus.COMPLETED);
            job.setCompletedAt(System.currentTimeMillis());

            // Check auto-verify threshold from config
            int autoVerifyThreshold = plugin.getConfigManager().getInt("jobs.require-staff-verify-above");

            if (job.getPointsOffered() <= autoVerifyThreshold) {
                // Auto-verify
                return storage.updateJob(job).thenCompose(v -> verifyJobInternal(job, null));
            }

            return storage.updateJob(job).thenApply(v -> true);
        });
    }

    /**
     * Staff verifies a completed job, triggering reputation payout.
     */
    public CompletableFuture<Boolean> verifyJob(long jobId, UUID staffUuid) {
        return storage.getJob(jobId).thenCompose(job -> {
            if (job == null || job.getStatus() != JobStatus.COMPLETED) {
                return CompletableFuture.completedFuture(false);
            }

            return verifyJobInternal(job, staffUuid);
        });
    }

    private CompletableFuture<Boolean> verifyJobInternal(Job job, UUID staffUuid) {
        job.setStatus(JobStatus.VERIFIED);
        job.setVerifiedAt(System.currentTimeMillis());
        job.setVerifiedBy(staffUuid);

        return storage.updateJob(job).thenApply(v -> {
            // Grant reputation to the accepter (the one who did the work)
            if (job.getAccepterUuid() != null) {
                reputationManager.addReputation(
                        job.getAccepterUuid(),
                        job.getPointsOffered(),
                        "Job #" + job.getId() + " completed: " + job.getDescription(),
                        "JOB",
                        String.valueOf(job.getId())
                );
            }

            // Fire event on main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.getPluginManager().callEvent(new JobCompletedEvent(job));
            });

            return true;
        });
    }

    /**
     * Cancels a job. Only the poster or staff can cancel.
     */
    public CompletableFuture<Boolean> cancelJob(long jobId, UUID requesterUuid, boolean isStaff) {
        return storage.getJob(jobId).thenCompose(job -> {
            if (job == null) return CompletableFuture.completedFuture(false);
            if (job.getStatus() == JobStatus.VERIFIED || job.getStatus() == JobStatus.CANCELLED) {
                return CompletableFuture.completedFuture(false);
            }
            if (!isStaff && !job.getPosterUuid().equals(requesterUuid)) {
                return CompletableFuture.completedFuture(false);
            }

            job.setStatus(JobStatus.CANCELLED);
            return storage.updateJob(job).thenApply(v -> true);
        });
    }

    public CompletableFuture<Job> getJob(long id) {
        return storage.getJob(id);
    }

    public CompletableFuture<List<Job>> getOpenJobs() {
        return storage.getOpenJobs();
    }

    public CompletableFuture<List<Job>> getJobsByCategory(JobCategory category) {
        return storage.getJobsByCategory(category);
    }

    public JobStorage getStorage() {
        return storage;
    }
}
