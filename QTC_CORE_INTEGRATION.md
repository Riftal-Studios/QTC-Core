# QTC-Core Discord API Integration Guide

> **Target:** `qtc-core` PaperMC 1.21 plugin, Java 21, Gradle
> **Source of truth:** `qtc-discord` REST API (`services/api_service.py`)
> **Last updated:** 2026-02-07

---

## Table of Contents

1. [Overview](#1-overview)
2. [API Reference](#2-api-reference)
3. [What QTC-Core Needs to Implement](#3-what-qtc-core-needs-to-implement)
4. [Implementation Notes](#4-implementation-notes)
5. [Full Source Code](#5-full-source-code)
6. [Testing](#6-testing)

---

## 1. Overview

### What This Does

The `qtc-discord` bot exposes a REST API that acts as the **single source of truth** for the QTC community points system. When a player joins the Minecraft server or earns an achievement, the Minecraft plugin (`qtc-core`) sends an HTTP request to this API. The bot then:

- Looks up the player's linked Discord account
- Awards points (with deduplication — once-per-day joins, once-ever achievements)
- Checks for rank-ups and sends Discord notifications
- Announces challenge completions in the Discord server

### Architecture

```
┌──────────────┐       HTTP POST/GET       ┌──────────────────┐       Discord API
│              │  ───────────────────────►  │                  │  ──────────────►  Discord
│  Minecraft   │   Bearer token auth       │  qtc-discord     │                   Server
│  Server      │                           │  REST API        │
│  (qtc-core)  │  ◄───────────────────────  │  (aiohttp:8080)  │
│              │       JSON response       │                  │
└──────────────┘                           └──────────────────┘
                                                   │
                                                   ▼
                                             ┌───────────┐
                                             │  SQLite DB │
                                             │  (points,  │
                                             │   ranks,   │
                                             │   dedup)   │
                                             └───────────┘
```

### Why This Architecture?

- **No database access from Minecraft** — the bot owns the DB, preventing split-brain issues
- **Deduplication is server-side** — even if the plugin fires duplicate events, the API handles it
- **Rank-ups and notifications happen automatically** — the plugin doesn't need to know about Discord at all
- **Simple contract** — the plugin just sends "player X joined" / "player X got achievement Y"

---

## 2. API Reference

**Base URL:** Configured via `discord-api.url` in `config.yml` (e.g., `http://localhost:8080`)
**Authentication:** All endpoints except `/health` require a `Authorization: Bearer <token>` header.

### 2.1 `GET /health`

Health check. No authentication required.

**Request:**
```http
GET /health HTTP/1.1
Host: localhost:8080
```

**Response** `200 OK`:
```json
{
  "status": "ok"
}
```

---

### 2.2 `POST /api/minecraft/join`

Notify the bot that a player joined the Minecraft server. Awards 5 points once per calendar day per player. The bot uses `minecraft_uuid` as the stable identity key and will auto-update the stored `minecraft_name` if it differs from the incoming value (handles name changes).

**Request:**
```http
POST /api/minecraft/join HTTP/1.1
Host: localhost:8080
Authorization: Bearer your-secret-api-key
Content-Type: application/json

{
  "minecraft_uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
  "minecraft_name": "Steve"
}
```

| Field            | Type   | Required | Description                                          |
|------------------|--------|----------|------------------------------------------------------|
| `minecraft_uuid` | string | Yes      | Player's Minecraft UUID (`player.getUniqueId().toString()`) |
| `minecraft_name` | string | Yes      | Player's current Minecraft username (`player.getName()`) |

**Response — Points Awarded** `200 OK`:
```json
{
  "success": true,
  "awarded": true,
  "minecraft_uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
  "minecraft_name": "Steve",
  "discord_id": "123456789012345678",
  "points_awarded": 5,
  "new_rank": null
}
```

`new_rank` is a string (e.g., `"regular"`, `"trusted"`) when the player ranked up, otherwise `null`.

**Response — Already Joined Today (duplicate)** `200 OK`:
```json
{
  "success": true,
  "awarded": false,
  "reason": "not_linked"
}
```

> Note: The API returns `"reason": "not_linked"` for both unlinked players and already-joined-today cases when no user is found. The important field is `awarded: false`.

**Response — Player Not Linked** `200 OK`:
```json
{
  "success": true,
  "awarded": false,
  "reason": "not_linked"
}
```

---

### 2.3 `POST /api/minecraft/achievement`

Notify the bot that a player earned an advancement. Awards 10 points for regular advancements, 25 points for challenges. Each achievement is tracked per-player (by UUID) and only awarded once ever. The bot auto-updates the stored name if it differs.

**Request:**
```http
POST /api/minecraft/achievement HTTP/1.1
Host: localhost:8080
Authorization: Bearer your-secret-api-key
Content-Type: application/json

{
  "minecraft_uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
  "minecraft_name": "Steve",
  "achievement_name": "minecraft:story/mine_diamond",
  "is_challenge": false
}
```

| Field              | Type    | Required | Description                                          |
|--------------------|---------|----------|------------------------------------------------------|
| `minecraft_uuid`   | string  | Yes      | Player's Minecraft UUID (`player.getUniqueId().toString()`) |
| `minecraft_name`   | string  | Yes      | Player's current Minecraft username (`player.getName()`) |
| `achievement_name` | string  | Yes      | Advancement key (e.g., `minecraft:story/mine_diamond`) |
| `is_challenge`     | boolean | No       | `true` if this is a challenge frame advancement. Defaults to `false`. |

**Response — Points Awarded** `200 OK`:
```json
{
  "success": true,
  "awarded": true,
  "minecraft_uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
  "minecraft_name": "Steve",
  "discord_id": "123456789012345678",
  "achievement_name": "minecraft:story/mine_diamond",
  "points_awarded": 10,
  "new_rank": null
}
```

**Response — Challenge Awarded** `200 OK`:
```json
{
  "success": true,
  "awarded": true,
  "minecraft_uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
  "minecraft_name": "Steve",
  "discord_id": "123456789012345678",
  "achievement_name": "minecraft:nether/return_to_sender",
  "points_awarded": 25,
  "new_rank": "regular"
}
```

**Response — Already Earned / Not Linked** `200 OK`:
```json
{
  "success": true,
  "awarded": false,
  "reason": "not_linked"
}
```

---

### 2.4 `GET /api/player/{minecraft_uuid}`

Look up a player's linked Discord account and point balances by their Minecraft UUID.

**Request:**
```http
GET /api/player/069a79f4-44e9-4726-a5be-fca90e38aaf5 HTTP/1.1
Host: localhost:8080
Authorization: Bearer your-secret-api-key
```

**Response — Linked Player** `200 OK`:
```json
{
  "success": true,
  "linked": true,
  "minecraft_uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
  "minecraft_name": "Steve",
  "discord_id": "123456789012345678",
  "current_points": 150,
  "lifetime_points": 420,
  "rank_tier": "trusted"
}
```

**Response — Not Linked** `200 OK`:
```json
{
  "success": true,
  "linked": false,
  "minecraft_uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5"
}
```

---

### 2.5 `POST /api/link/generate`

Generate a one-time code that a Minecraft player can use to link their account in Discord. Called from the `/qtc link` Minecraft command. Both UUID and name are required so the bot stores both for stable identity.

**Request:**
```http
POST /api/link/generate HTTP/1.1
Host: localhost:8080
Authorization: Bearer your-secret-api-key
Content-Type: application/json

{
  "minecraft_uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
  "minecraft_name": "Steve"
}
```

| Field            | Type   | Required | Description                                          |
|------------------|--------|----------|------------------------------------------------------|
| `minecraft_uuid` | string | Yes      | Player's Minecraft UUID (`player.getUniqueId().toString()`) |
| `minecraft_name` | string | Yes      | Player's current Minecraft username (`player.getName()`) |

**Response — Code Generated** `200 OK`:
```json
{
  "success": true,
  "code": "A3K7MN",
  "expires_in": 300
}
```

| Field        | Type    | Description                               |
|--------------|---------|-------------------------------------------|
| `success`    | boolean | `true` when code was generated            |
| `code`       | string  | 6-character alphanumeric code (uppercase) |
| `expires_in` | int     | Seconds until the code expires (300 = 5 min) |

**Response — Already Linked** `200 OK`:
```json
{
  "success": false,
  "error": "already_linked",
  "message": "This Minecraft account is already linked to a Discord account."
}
```

**How linking works end-to-end:**

1. Player types `/qtc link` in Minecraft
2. Plugin calls `POST /api/link/generate` with the player's UUID and name
3. Bot generates a 6-character OTP code (5-minute expiry) and returns it
4. Plugin shows the code to the player in chat
5. Player types `/link A3K7MN` in Discord
6. Bot validates the code, verifies ownership, links the accounts
7. Code is deleted (single-use)

**Security properties:**
- Codes are 6 characters from a 32-char unambiguous alphabet (no 0/O, 1/I/L)
- Generated with `secrets.choice()` (cryptographic randomness)
- Single-use: deleted on successful link
- One-per-player: generating a new code invalidates the old one
- Rate limited: 5 failed Discord attempts per 15 minutes

---

### 2.6 Error Responses

All error responses follow the same shape:

**`400 Bad Request`** — Invalid JSON or missing required fields:
```json
{
  "error": "Invalid JSON body"
}
```
```json
{
  "error": "Missing required fields: minecraft_uuid, minecraft_name"
}
```
```json
{
  "error": "Missing required fields: minecraft_uuid, minecraft_name, achievement_name"
}
```

**`401 Unauthorized`** — Missing or malformed Authorization header:
```json
{
  "error": "Missing or malformed Authorization header"
}
```

**`403 Forbidden`** — Invalid API key:
```json
{
  "error": "Invalid API key"
}
```

---

## 3. What QTC-Core Needs to Implement

### 3a. Configuration — `config.yml` Additions

Add this section to `src/main/resources/config.yml`:

```yaml
# Discord Bot API Integration
# The bot must be running with API_KEY and API_PORT set in its .env
discord-api:
  enabled: true
  url: "http://localhost:8080"
  key: "your-secret-api-key"
  timeout: 5000
```

| Key                   | Type    | Default                  | Description                           |
|-----------------------|---------|--------------------------|---------------------------------------|
| `discord-api.enabled` | boolean | `true`                   | Toggle the entire integration on/off  |
| `discord-api.url`     | string  | `http://localhost:8080`  | Base URL of the bot's REST API        |
| `discord-api.key`     | string  | —                        | Bearer token (must match bot's `API_KEY` env var) |
| `discord-api.timeout` | int     | `5000`                   | HTTP connection/request timeout in milliseconds |

### 3b. New Java Classes

Create these files under `src/main/java/com/riftalstudios/qtccore/`:

| File | Package | Purpose |
|------|---------|---------|
| `api/DiscordAPIClient.java` | `...qtccore.api` | HTTP client wrapper — sends requests to the bot API |
| `api/DiscordAPIResponse.java` | `...qtccore.api` | Response record classes for type-safe deserialization |
| `api/DiscordAPIManager.java` | `...qtccore.api` | Manager lifecycle — reads config, creates client, registers listener |
| `listeners/DiscordIntegrationListener.java` | `...qtccore.listeners` | Event listener for `PlayerJoinEvent` and `PlayerAdvancementDoneEvent` |

### 3c. Account Linking Command

Add a `/qtc link` subcommand that calls `DiscordAPIClient.requestLinkCode(playerUuid, playerName)` and displays the returned code to the player. The flow is documented in [section 2.5](#25-post-apilinkgenerate). Key points:

- Call is async — must not block the main thread
- On success (`response.success() == true`): show the player the `code` and tell them to type `/link <code>` in Discord within 5 minutes
- On failure (`success == false`): show the `message` field (e.g., "already linked")
- On HTTP/network error: show a generic "could not reach the bot" message
- The base `/qtc` command must be accessible to all players for this subcommand to work; enforce permissions at the subcommand level if needed

### 3d. Modifications to Existing Files

**`QTCCore.java`** — Register the new manager in `onEnable()`, before `enableManagers()`:

```java
// In QTCCore.onEnable(), after nodeTemplateManager registration:

import com.riftalstudios.qtccore.api.DiscordAPIManager;

// ...

DiscordAPIManager discordAPIManager = new DiscordAPIManager(this);
registerManager(discordAPIManager);
```

**`config.yml`** — Add the `discord-api` section shown above.

**`plugin.yml`** — No changes needed. Java 21's `java.net.http.HttpClient` and Paper's bundled Gson require no additional library declarations.

---

## 4. Implementation Notes

### Thread Safety

All HTTP calls **must** run off the main server thread. The Minecraft server's main thread handles tick processing — blocking it with network I/O causes lag or watchdog kills.

Use `Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> { ... })` or `CompletableFuture.supplyAsync()` with a custom executor.

### Advancement Filtering

Minecraft fires `PlayerAdvancementDoneEvent` for **every** advancement, including hundreds of recipe unlocks. Filter these out:

```java
String key = advancement.getKey().toString();

// Skip recipe advancements — there are hundreds and they're not real achievements
if (key.startsWith("minecraft:recipes/")) {
    return;
}

// Skip root advancements (e.g., "minecraft:story/root") — these are just tab openers
if (key.endsWith("/root")) {
    return;
}
```

### Challenge Detection

Challenges are special advancements with a `CHALLENGE` frame in their display data:

```java
AdvancementDisplay display = advancement.getDisplay();
if (display != null) {
    boolean isChallenge = display.frame() == AdvancementDisplay.Frame.CHALLENGE;
}
```

If `getDisplay()` returns `null`, treat it as a non-challenge (it's likely a hidden/internal advancement).

### Error Handling

**Never crash the server for a failed API call.** Log the error and move on:

```java
try {
    client.notifyJoin(uuid, name);
} catch (Exception e) {
    plugin.getLogger().warning("Discord API call failed: " + e.getMessage());
}
```

### Player Identity (UUID + Name)

All API calls require **both** `minecraft_uuid` and `minecraft_name`:

```java
String uuid = player.getUniqueId().toString();  // Stable identity — never changes
String name = player.getName();                  // Display name — can change
```

The bot uses `minecraft_uuid` as the primary key for deduplication and account linking. The `minecraft_name` is stored alongside for display purposes and is **auto-updated** by the bot whenever it receives a UUID with a different name than what's stored. This means player name changes are handled transparently — no manual re-linking needed.

### Point Values (for reference)

These are configured on the bot side. The plugin doesn't need to know them, but for context:

| Event | Points | Frequency |
|-------|--------|-----------|
| Minecraft join | 5 | Once per calendar day |
| Achievement (regular) | 10 | Once ever per achievement |
| Achievement (challenge) | 25 | Once ever per achievement |

### Rank Tiers (for reference)

| Tier | Emoji | Min Lifetime Points |
|------|-------|---------------------|
| newcomer | `🌱` | 0 |
| regular | `🌿` | (configured in bot) |
| trusted | `🌳` | (configured in bot) |
| veteran | `⭐` | (configured in bot) |
| legend | `👑` | (configured in bot) |

---

## 5. Full Source Code

### 5.1 `api/DiscordAPIResponse.java`

```java
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
```

### 5.2 `api/DiscordAPIClient.java`

```java
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
                    logger.warning("Player lookup error for " + playerName + ": " + ex.getMessage());
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
```

### 5.3 `listeners/DiscordIntegrationListener.java`

```java
package com.riftalstudios.qtccore.listeners;

import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.api.DiscordAPIClient;
import io.papermc.paper.advancement.AdvancementDisplay;
import org.bukkit.Bukkit;
import org.bukkit.advancement.Advancement;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Listens for Minecraft events and forwards them to the Discord bot API.
 * All API calls run asynchronously to avoid blocking the main server thread.
 */
public class DiscordIntegrationListener implements Listener {

    private final QTCCore plugin;
    private final DiscordAPIClient client;

    public DiscordIntegrationListener(QTCCore plugin, DiscordAPIClient client) {
        this.plugin = plugin;
        this.client = client;
    }

    /**
     * Fires when a player joins the server.
     * Awards 5 points once per calendar day via the Discord bot API.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        String playerUuid = event.getPlayer().getUniqueId().toString();
        String playerName = event.getPlayer().getName();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                client.notifyJoin(playerUuid, playerName).join();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to notify Discord API of join for "
                        + playerName + ": " + e.getMessage());
            }
        });
    }

    /**
     * Fires when a player completes an advancement.
     * Filters out recipe unlocks and root advancements, then notifies the API.
     * Challenges (special frame type) earn bonus points.
     */
    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        Advancement advancement = event.getAdvancement();
        String key = advancement.getKey().toString();

        // Filter out recipe advancements — hundreds of these fire and they're not real achievements
        if (key.startsWith("minecraft:recipes/")) {
            return;
        }

        // Filter out root advancements (tab openers like "minecraft:story/root")
        if (key.endsWith("/root")) {
            return;
        }

        // Determine if this is a challenge
        AdvancementDisplay display = advancement.getDisplay();
        if (display == null) {
            // No display data means it's a hidden/internal advancement — skip
            return;
        }

        boolean isChallenge = display.frame() == AdvancementDisplay.Frame.CHALLENGE;
        String playerUuid = event.getPlayer().getUniqueId().toString();
        String playerName = event.getPlayer().getName();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                client.notifyAchievement(playerUuid, playerName, key, isChallenge).join();
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to notify Discord API of advancement for "
                        + playerName + ": " + e.getMessage());
            }
        });
    }
}
```

### 5.4 `api/DiscordAPIManager.java`

```java
package com.riftalstudios.qtccore.api;

import com.riftalstudios.qtccore.Manager;
import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.listeners.DiscordIntegrationListener;
import org.bukkit.Bukkit;

/**
 * Manager for the Discord bot API integration.
 * Reads config, creates the HTTP client, registers event listeners.
 * Implements the Manager interface for lifecycle management by QTCCore.
 */
public class DiscordAPIManager implements Manager {

    private final QTCCore plugin;
    private DiscordAPIClient client;

    public DiscordAPIManager(QTCCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        // Check if integration is enabled in config
        if (!plugin.getConfig().getBoolean("discord-api.enabled", true)) {
            plugin.getLogger().info("Discord API integration is disabled in config");
            return;
        }

        // Read config values
        String url = plugin.getConfig().getString("discord-api.url", "http://localhost:8080");
        String key = plugin.getConfig().getString("discord-api.key", "");
        int timeout = plugin.getConfig().getInt("discord-api.timeout", 5000);

        // Validate required config
        if (key.isEmpty()) {
            plugin.getLogger().warning("Discord API key not set in config.yml — integration disabled. "
                    + "Set 'discord-api.key' to the same value as API_KEY in the bot's .env file.");
            return;
        }

        // Create HTTP client
        client = new DiscordAPIClient(url, key, timeout, plugin.getLogger());
        plugin.getLogger().info("Discord API client created for " + url);

        // Register event listener
        DiscordIntegrationListener listener = new DiscordIntegrationListener(plugin, client);
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        plugin.getLogger().info("Discord integration listener registered");

        // Optional: health check on startup
        client.healthCheck().thenAccept(healthy -> {
            if (healthy) {
                plugin.getLogger().info("Discord API health check passed");
            } else {
                plugin.getLogger().warning("Discord API health check failed — "
                        + "is the bot running? Check discord-api.url in config.yml");
            }
        });
    }

    @Override
    public void disable() {
        if (client != null) {
            client.shutdown();
            plugin.getLogger().info("Discord API client shut down");
        }
    }

    @Override
    public String getName() {
        return "DiscordAPIManager";
    }

    /**
     * Get the API client instance (may be null if disabled or not configured).
     */
    public DiscordAPIClient getClient() {
        return client;
    }
}
```

### 5.5 Updated `QTCCore.java` — Registration Diff

Add these lines to `QTCCore.java`:

```diff
 import com.riftalstudios.qtccore.config.ConfigManager;
 import com.riftalstudios.qtccore.config.MessageConfig;
+import com.riftalstudios.qtccore.api.DiscordAPIManager;
 import com.riftalstudios.qtccore.resources.ResourceNodeManager;
```

```diff
     private ResourceNodeManager resourceNodeManager;
     private NodeTemplateManager nodeTemplateManager;
+    private DiscordAPIManager discordAPIManager;
```

```diff
         nodeTemplateManager = new NodeTemplateManager(this);
         registerManager(nodeTemplateManager);

+        discordAPIManager = new DiscordAPIManager(this);
+        registerManager(discordAPIManager);
+
         registerCommands();
```

### 5.6 Updated `config.yml`

Add to the bottom of `src/main/resources/config.yml`:

```yaml
# Discord Bot API Integration
# Links Minecraft events (joins, achievements) to the Discord points system.
# The qtc-discord bot must be running with API_KEY and API_PORT env vars set.
# Set 'key' to the same value as the bot's API_KEY.
discord-api:
  enabled: true
  url: "http://localhost:8080"
  key: ""
  timeout: 5000
```

---

## 6. Testing

### 6.1 Manual API Testing with curl

Before writing any Java code, verify the bot's API is working:

**Health check (no auth):**
```bash
curl http://localhost:8080/health
# Expected: {"status": "ok"}
```

**Join notification:**
```bash
curl -X POST http://localhost:8080/api/minecraft/join \
  -H "Authorization: Bearer your-secret-api-key" \
  -H "Content-Type: application/json" \
  -d '{"minecraft_uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5", "minecraft_name": "Steve"}'
# Expected: {"success": true, "awarded": true, ...} or {"success": true, "awarded": false, ...}
```

**Achievement notification:**
```bash
curl -X POST http://localhost:8080/api/minecraft/achievement \
  -H "Authorization: Bearer your-secret-api-key" \
  -H "Content-Type: application/json" \
  -d '{"minecraft_uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5", "minecraft_name": "Steve", "achievement_name": "minecraft:story/mine_diamond", "is_challenge": false}'
```

**Challenge notification:**
```bash
curl -X POST http://localhost:8080/api/minecraft/achievement \
  -H "Authorization: Bearer your-secret-api-key" \
  -H "Content-Type: application/json" \
  -d '{"minecraft_uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5", "minecraft_name": "Steve", "achievement_name": "minecraft:nether/return_to_sender", "is_challenge": true}'
```

**Player lookup:**
```bash
curl http://localhost:8080/api/player/069a79f4-44e9-4726-a5be-fca90e38aaf5 \
  -H "Authorization: Bearer your-secret-api-key"
```

**Generate link code:**
```bash
curl -X POST http://localhost:8080/api/link/generate \
  -H "Authorization: Bearer your-secret-api-key" \
  -H "Content-Type: application/json" \
  -d '{"minecraft_uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5", "minecraft_name": "Steve"}'
# Expected: {"success": true, "code": "A3K7MN", "expires_in": 300}
```

**Test auto-name-update (same UUID, different name):**
```bash
curl -X POST http://localhost:8080/api/minecraft/join \
  -H "Authorization: Bearer your-secret-api-key" \
  -H "Content-Type: application/json" \
  -d '{"minecraft_uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5", "minecraft_name": "Steve_NewName"}'
# The bot will auto-update the stored name from "Steve" to "Steve_NewName"
```

**Test auth errors:**
```bash
# Missing header → 401
curl http://localhost:8080/api/player/069a79f4-44e9-4726-a5be-fca90e38aaf5

# Bad token → 403
curl http://localhost:8080/api/player/069a79f4-44e9-4726-a5be-fca90e38aaf5 \
  -H "Authorization: Bearer wrong-key"

# Bad JSON → 400
curl -X POST http://localhost:8080/api/minecraft/join \
  -H "Authorization: Bearer your-secret-api-key" \
  -H "Content-Type: application/json" \
  -d 'not json'

# Missing field → 400
curl -X POST http://localhost:8080/api/minecraft/join \
  -H "Authorization: Bearer your-secret-api-key" \
  -H "Content-Type: application/json" \
  -d '{}'
```

### 6.2 End-to-End Verification

1. **Start the Discord bot** with `API_KEY` and `API_PORT` in `.env`
2. **Verify the API** is running: `curl http://localhost:8080/health`
3. **Link a test account**: generate a code via curl or `/qtc link` in Minecraft, then use `/link <code>` in Discord
4. **Build and install qtc-core** with the new integration code
5. **Set the config** in `plugins/QTCCore/config.yml`:
   ```yaml
   discord-api:
     enabled: true
     url: "http://localhost:8080"
     key: "same-as-bot-API_KEY"
     timeout: 5000
   ```
6. **Start/reload the Minecraft server** and check logs for:
   ```
   [QTCCore] Discord API client created for http://localhost:8080
   [QTCCore] Discord integration listener registered
   [QTCCore] Discord API health check passed
   ```
7. **Join the Minecraft server** — check the bot logs for an incoming join request
8. **Earn an advancement** in-game — verify the bot awards points
9. **Complete a challenge** (e.g., use a test command or adventure mode) — verify the bot sends a channel announcement
10. **Rejoin the server** — verify points are NOT awarded again (dedup working)
11. **Check Discord** — verify any rank-up notifications were sent

### 6.3 Troubleshooting

| Symptom | Check |
|---------|-------|
| "Discord API key not set" in server log | Set `discord-api.key` in `plugins/QTCCore/config.yml` |
| Health check fails | Is the bot running? Is the URL correct? Is the port open? |
| `401` errors in server log | The `Authorization` header format may be wrong — check client code |
| `403` errors in server log | The key in `config.yml` doesn't match the bot's `API_KEY` env var |
| Points not awarded | Is the Minecraft name linked via `/link` in Discord? |
| Duplicate points | This shouldn't happen — the bot handles dedup. Check bot logs. |
| Server lag on join | HTTP calls should be async. Verify `runTaskAsynchronously` is used. |
| `java.net.ConnectException` | Bot is not running or URL/port is wrong |
| Link code "already linked" | Player's MC name already linked — they should `/unlink` in Discord first |
| Link code expired | Player took longer than 5 minutes — generate a new code |
| Rate limited on `/link` | Player failed 5+ times in Discord — wait 15 minutes |
