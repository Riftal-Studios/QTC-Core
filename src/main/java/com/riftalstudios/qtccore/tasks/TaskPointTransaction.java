package com.riftalstudios.qtccore.tasks;

import java.util.UUID;

public record TaskPointTransaction(
        long id,
        UUID uuid,
        long amount,
        String reason,
        long taskId,
        long balanceAfter,
        long totalAfter,
        UUID approvedBy,
        long createdAt
) {
}
