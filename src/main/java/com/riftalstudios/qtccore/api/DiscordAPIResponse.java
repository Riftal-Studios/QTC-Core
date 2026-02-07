package com.riftalstudios.qtccore.api;

/**
 * Type-safe response records for the Discord bot REST API.
 * Uses Java 21 records for immutable data carriers.
 */
public final class DiscordAPIResponse {

    private DiscordAPIResponse() {}

    /**
     * Response from POST /api/minecraft/join
     *
     * @param success       always true for 200 responses
     * @param awarded       true if points were actually awarded
     * @param reason        "not_linked" when player has no linked Discord account, null otherwise
     * @param minecraftUuid the player's Minecraft UUID
     * @param minecraftName the player's Minecraft username
     * @param discordId     the linked Discord user ID, null if not linked
     * @param pointsAwarded number of points awarded (0 if not awarded)
     * @param newRank       new rank tier if player ranked up, null otherwise
     */
    public record JoinResponse(
            boolean success,
            boolean awarded,
            String reason,
            String minecraftUuid,
            String minecraftName,
            String discordId,
            int pointsAwarded,
            String newRank
    ) {}

    /**
     * Response from POST /api/minecraft/achievement
     *
     * @param success         always true for 200 responses
     * @param awarded         true if points were actually awarded
     * @param reason          "not_linked" when player has no linked Discord account, null otherwise
     * @param minecraftUuid   the player's Minecraft UUID
     * @param minecraftName   the player's Minecraft username
     * @param discordId       the linked Discord user ID, null if not linked
     * @param achievementName the advancement key that was reported
     * @param pointsAwarded   number of points awarded (0 if not awarded)
     * @param newRank         new rank tier if player ranked up, null otherwise
     */
    public record AchievementResponse(
            boolean success,
            boolean awarded,
            String reason,
            String minecraftUuid,
            String minecraftName,
            String discordId,
            String achievementName,
            int pointsAwarded,
            String newRank
    ) {}

    /**
     * Response from GET /api/player/{minecraft_uuid}
     *
     * @param success        always true for 200 responses
     * @param linked         true if the player has a linked Discord account
     * @param minecraftUuid  the player's Minecraft UUID
     * @param minecraftName  the player's Minecraft username
     * @param discordId      the linked Discord user ID, null if not linked
     * @param currentPoints  spendable point balance, 0 if not linked
     * @param lifetimePoints total points ever earned, 0 if not linked
     * @param rankTier       current rank tier name, null if not linked
     */
    public record PlayerResponse(
            boolean success,
            boolean linked,
            String minecraftUuid,
            String minecraftName,
            String discordId,
            int currentPoints,
            int lifetimePoints,
            String rankTier
    ) {}

    /**
     * Response from POST /api/link/generate
     *
     * @param success   true if a code was generated
     * @param code      the 6-character OTP code (null on failure)
     * @param expiresIn seconds until the code expires (300 = 5 min)
     * @param error     error key on failure (e.g., "already_linked"), null on success
     * @param message   human-readable error message on failure, null on success
     */
    public record LinkCodeResponse(
            boolean success,
            String code,
            int expiresIn,
            String error,
            String message
    ) {}
}
