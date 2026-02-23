package com.riftalstudios.qtccore.reputation;

import java.util.UUID;

public record ReputationTransaction(
        long id,
        UUID uuid,
        long amount,
        String reason,
        String sourceType,
        String sourceId,
        long balanceAfter,
        long totalAfter,
        long createdAt
) {
}
