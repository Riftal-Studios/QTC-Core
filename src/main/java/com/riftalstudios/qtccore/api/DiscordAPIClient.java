package com.riftalstudios.qtccore.api;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.riftalstudios.qtccore.api.DiscordAPIResponse.AchievementResponse;
import com.riftalstudios.qtccore.api.DiscordAPIResponse.JoinResponse;
import com.riftalstudios.qtccore.api.DiscordAPIResponse.LinkCodeResponse;
import com.riftalstudios.qtccore.api.DiscordAPIResponse.PlayerResponse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * HTTP client wrapper for the qtc-discord REST API.
 * Uses Java 21's built-in HttpClient — no additional dependencies needed.
 *
 * All public methods return CompletableFuture and run on the HttpClient's
 * async executor, keeping the Minecraft main thread free.
 */
public class DiscordAPIClient {

    private final HttpClient httpClient;
    private final String baseUrl;
    private final String apiKey;
    private final Logger logger;
    private final Gson gson;

    /**
     * @param baseUrl   Base URL of the Discord bot API (e.g., "http://localhost:8080")
     * @param apiKey    Bearer token for authentication
     * @param timeoutMs Connection and request timeout in milliseconds
     * @param logger    Plugin logger instance
     */
    public DiscordAPIClient(String baseUrl, String apiKey, int timeoutMs, Logger logger) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
        this.logger = logger;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();

        // The API uses snake_case JSON keys (e.g., minecraft_name, discord_id)
        this.gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .create();
    }

    /**
     * Notify the bot that a player joined the server.
     * Awards points once per calendar day per player.
     *
     * @param playerUuid Minecraft UUID (from player.getUniqueId().toString())
     * @param playerName Minecraft username (from player.getName())
     * @return CompletableFuture with the join response
     */
    public CompletableFuture<JoinResponse> notifyJoin(String playerUuid, String playerName) {
        String json = gson.toJson(Map.of("minecraft_uuid", playerUuid, "minecraft_name", playerName));

        HttpRequest request = buildPostRequest("/api/minecraft/join", json);

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        logger.warning("Join notification failed [" + response.statusCode() + "]: " + response.body());
                        return null;
                    }
                    return gson.fromJson(response.body(), JoinResponse.class);
                })
                .exceptionally(ex -> {
                    logger.warning("Join notification error for " + playerName + ": " + ex.getMessage());
                    return null;
                });
    }

    /**
     * Notify the bot that a player earned an advancement.
     * Awards 10 points for regular advancements, 25 for challenges (once ever per achievement).
     *
     * @param playerUuid      Minecraft UUID (from player.getUniqueId().toString())
     * @param playerName      Minecraft username (from player.getName())
     * @param achievementName Advancement key (e.g., "minecraft:story/mine_diamond")
     * @param isChallenge     true if the advancement frame is CHALLENGE
     * @return CompletableFuture with the achievement response
     */
    public CompletableFuture<AchievementResponse> notifyAchievement(
            String playerUuid, String playerName, String achievementName, boolean isChallenge) {

        String json = gson.toJson(Map.of(
                "minecraft_uuid", playerUuid,
                "minecraft_name", playerName,
                "achievement_name", achievementName,
                "is_challenge", isChallenge
        ));

        HttpRequest request = buildPostRequest("/api/minecraft/achievement", json);

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        logger.warning("Achievement notification failed [" + response.statusCode() + "]: "
                                + response.body());
                        return null;
                    }
                    return gson.fromJson(response.body(), AchievementResponse.class);
                })
                .exceptionally(ex -> {
                    logger.warning("Achievement notification error for " + playerName + ": " + ex.getMessage());
                    return null;
                });
    }

    /**
     * Look up a player's linked Discord account and point balances.
     *
     * @param playerUuid Minecraft UUID (from player.getUniqueId().toString())
     * @return CompletableFuture with the player response
     */
    public CompletableFuture<PlayerResponse> getPlayer(String playerUuid) {
        HttpRequest request = buildGetRequest("/api/player/" + playerUuid);

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        logger.warning("Player lookup failed [" + response.statusCode() + "]: " + response.body());
                        return null;
                    }
                    return gson.fromJson(response.body(), PlayerResponse.class);
                })
                .exceptionally(ex -> {
                    logger.warning("Player lookup error for " + playerUuid + ": " + ex.getMessage());
                    return null;
                });
    }

    /**
     * Request a one-time link code for a Minecraft player.
     * The player shows this code in Discord via /link to prove account ownership.
     *
     * @param playerUuid Minecraft UUID (from player.getUniqueId().toString())
     * @param playerName Minecraft username (from player.getName())
     * @return CompletableFuture with the link code response
     */
    public CompletableFuture<LinkCodeResponse> requestLinkCode(String playerUuid, String playerName) {
        String json = gson.toJson(Map.of("minecraft_uuid", playerUuid, "minecraft_name", playerName));

        HttpRequest request = buildPostRequest("/api/link/generate", json);

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        logger.warning("Link code request failed [" + response.statusCode() + "]: " + response.body());
                        return null;
                    }
                    return gson.fromJson(response.body(), LinkCodeResponse.class);
                })
                .exceptionally(ex -> {
                    logger.warning("Link code request error for " + playerName + ": " + ex.getMessage());
                    return null;
                });
    }

    /**
     * Check if the API is reachable. Does not require authentication.
     *
     * @return CompletableFuture that resolves to true if healthy
     */
    public CompletableFuture<Boolean> healthCheck() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/health"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> response.statusCode() == 200)
                .exceptionally(ex -> {
                    logger.warning("Health check failed: " + ex.getMessage());
                    return false;
                });
    }

    /**
     * Shut down the underlying HTTP client executor.
     * Call this on plugin disable to avoid thread leaks.
     */
    public void shutdown() {
        httpClient.close();
    }

    // -- Internal helpers --

    private HttpRequest buildPostRequest(String path, String jsonBody) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
    }

    private HttpRequest buildGetRequest(String path) {
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
    }
}
