package com.riftalstudios.qtccore.tasks;

import com.riftalstudios.qtccore.Manager;
import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.profile.PlayerProfile;
import com.riftalstudios.qtccore.profile.ProfileManager;
import com.riftalstudios.qtccore.storage.DatabaseManager;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class StaffTaskManager implements Manager {

    private final QTCCore plugin;
    private final StaffTaskStorage storage;
    private final ProfileManager profileManager;

    public StaffTaskManager(QTCCore plugin, DatabaseManager database, ProfileManager profileManager) {
        this.plugin = plugin;
        this.storage = new StaffTaskStorage(plugin, database);
        this.profileManager = profileManager;
    }

    @Override
    public void enable() {
        plugin.getLogger().info("StaffTaskManager enabled");
    }

    @Override
    public void disable() {
        plugin.getLogger().info("StaffTaskManager disabled");
    }

    @Override
    public String getName() {
        return "StaffTaskManager";
    }

    /**
     * Creates a new staff task.
     */
    public CompletableFuture<StaffTask> createTask(String title, String description,
                                                    UUID creatorUuid, UUID assigneeUuid,
                                                    int suggestedPoints) {
        if (suggestedPoints < 0) suggestedPoints = 0;
        if (suggestedPoints > 100) suggestedPoints = 100;

        StaffTask task = new StaffTask(title, description, creatorUuid, assigneeUuid, suggestedPoints);
        return storage.insertTask(task).thenApply(id -> task);
    }

    /**
     * Assigns a task to a staff member.
     */
    public CompletableFuture<Boolean> assignTask(long taskId, UUID assigneeUuid) {
        return storage.getTask(taskId).thenCompose(task -> {
            if (task == null) return CompletableFuture.completedFuture(false);
            if (task.getStatus() != TaskStatus.CREATED && task.getStatus() != TaskStatus.ASSIGNED) {
                return CompletableFuture.completedFuture(false);
            }

            task.setAssigneeUuid(assigneeUuid);
            task.setStatus(TaskStatus.ASSIGNED);
            task.setAssignedAt(System.currentTimeMillis());

            return storage.updateTask(task).thenApply(v -> true);
        });
    }

    /**
     * Assignee starts working on the task.
     */
    public CompletableFuture<Boolean> startTask(long taskId, UUID requesterUuid) {
        return storage.getTask(taskId).thenCompose(task -> {
            if (task == null || task.getStatus() != TaskStatus.ASSIGNED) {
                return CompletableFuture.completedFuture(false);
            }
            if (!task.getAssigneeUuid().equals(requesterUuid)) {
                return CompletableFuture.completedFuture(false);
            }

            task.setStatus(TaskStatus.IN_PROGRESS);
            return storage.updateTask(task).thenApply(v -> true);
        });
    }

    /**
     * Assignee submits the task for review.
     */
    public CompletableFuture<Boolean> submitTask(long taskId, UUID requesterUuid) {
        return storage.getTask(taskId).thenCompose(task -> {
            if (task == null || task.getStatus() != TaskStatus.IN_PROGRESS) {
                return CompletableFuture.completedFuture(false);
            }
            if (!task.getAssigneeUuid().equals(requesterUuid)) {
                return CompletableFuture.completedFuture(false);
            }

            task.setStatus(TaskStatus.SUBMITTED);
            task.setSubmittedAt(System.currentTimeMillis());
            return storage.updateTask(task).thenApply(v -> true);
        });
    }

    /**
     * Admin/Owner approves a submitted task and credits task points.
     */
    public CompletableFuture<Boolean> approveTask(long taskId, UUID approverUuid, int approvedPoints) {
        int finalPoints = Math.max(approvedPoints, 0);
        return storage.getTask(taskId).thenCompose(task -> {
            if (task == null || task.getStatus() != TaskStatus.SUBMITTED) {
                return CompletableFuture.completedFuture(false);
            }

            task.setStatus(TaskStatus.APPROVED);
            task.setApprovedBy(approverUuid);
            task.setApprovedPoints(finalPoints);
            task.setResolvedAt(System.currentTimeMillis());

            return storage.updateTask(task).thenCompose(v -> {
                // Credit task points to assignee
                if (task.getAssigneeUuid() != null && finalPoints > 0) {
                    return creditTaskPoints(task.getAssigneeUuid(), finalPoints, task.getId(), approverUuid,
                            "Task #" + task.getId() + " approved: " + task.getTitle());
                }
                return CompletableFuture.completedFuture((Void) null);
            }).thenApply(v -> true);
        });
    }

    /**
     * Admin/Owner rejects a submitted task.
     */
    public CompletableFuture<Boolean> rejectTask(long taskId, UUID rejectorUuid, String reason) {
        return storage.getTask(taskId).thenCompose(task -> {
            if (task == null || task.getStatus() != TaskStatus.SUBMITTED) {
                return CompletableFuture.completedFuture(false);
            }

            task.setStatus(TaskStatus.REJECTED);
            task.setApprovedBy(rejectorUuid);
            task.setResolvedAt(System.currentTimeMillis());

            return storage.updateTask(task).thenApply(v -> true);
        });
    }

    private CompletableFuture<Void> creditTaskPoints(UUID uuid, int points, long taskId,
                                                      UUID approvedBy, String reason) {
        return profileManager.getOrLoadProfile(uuid).thenCompose(profile -> {
            long newBalance = profile.getTaskPointBalance() + points;
            long newTotal = profile.getTaskPointTotal() + points;
            profile.setTaskPointBalance(newBalance);
            profile.setTaskPointTotal(newTotal);

            TaskPointTransaction txn = new TaskPointTransaction(
                    0, uuid, points, reason, taskId, newBalance, newTotal,
                    approvedBy, System.currentTimeMillis()
            );

            return storage.insertTaskPointTransaction(txn)
                    .thenCompose(v -> profileManager.saveProfile(profile));
        });
    }

    public CompletableFuture<StaffTask> getTask(long id) {
        return storage.getTask(id);
    }

    public CompletableFuture<List<StaffTask>> getTasksByStatus(TaskStatus status) {
        return storage.getTasksByStatus(status);
    }

    public CompletableFuture<List<StaffTask>> getTasksByAssignee(UUID uuid) {
        return storage.getTasksByAssignee(uuid);
    }

    public StaffTaskStorage getStorage() {
        return storage;
    }
}
