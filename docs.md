# QTC Bot: Ultimate Master Handoff Document (100% Comprehensive)



This document is the absolute source of truth for the developer and administration teams. It consolidates all design documents, technical guides, system philosophies, and granular mechanics into a single, exhaustive reference.



---



## 1. System Vision & Architecture

The goal is to create a "set it and forget it" managed ecosystem. The bot (QTC Discord Bot) manages player identity, staff workflows, and a cross-platform economy, allowing the Owner and Admins to focus on community and content rather than manually managing roles or stats.



### 1.1 Technical Foundation

- **Data Anchor**: Minecraft UUID (Universal Unique Identifier). Never rely on usernames for persistent storage.

- **Source of Truth**: The bot bridges Minecraft UUIDs with Discord Snowflakes.

- **REST API**: Used by Minecraft plugins to report joins, advancements, and challenges via a designated log channel.



---



## 2. Core System 1: Account Linking (Foundation)

Every linked player has one master profile that contains their global stats and manages their characters.



### 2.1 The Linking Process

1. **In-Game**: Player types `/qtc link`. Bot generates a 6-character One-Time Password (OTP).
2. **Discord**: Player types `/link <code>` within 5 minutes.
3. **Storage**: The bot binds the `Discord_ID` to the `Minecraft_UUID`.

- **Constraint**: One Discord account per Minecraft account. No alts unless explicitly approved.

- **Status tracking**: Stores `{DiscordID, UUID, Username, Date, Status}`.

- **Unlinking**: Requires confirmation; cooldowns apply (e.g., 7 days).



---



## 3. Core System 2: Player Profile & Identity Management

"Reputation" is for players. It tracks their worth, status, and community contribution.



### 3.1 Profile Identity Components

The profile card is the primary way players show off their status. It contains several key visual elements:

- **Primary Role Icon**: A unique visual anchor (Emoji or small Image) displayed next to the player's name. It represents their highest "Authority Role" (Staff, Admin, Faction Leader, etc.) and immediately signals their level of permission or responsibility.

- **Selected Badges (1-3 slots)**: These are the "Trophies" that represent specific accomplishments across any character (e.g., "Slayer of the Void," "Veteran Builder"). Players can choose up to 3 unlocked badges to "pin" to their main profile.

- **Selected Service Identities (1-2 slots)**: These are the "Social Labels" or "Callings" (e.g., "Master Farmer," "Dungeon Guide"). Players choose up to 2 to signal what expertise they offer to the community.



### 3.2 Layered Profile UI (Tabs)

The profile is a multi-page interactive UI (Buttons/Menus):

- **Tab 1: Main View**: Minecraft name, Discord name, Rank, Primary Role Icon, Total Rep, Last Active, Selected Badges (1-3), and Selected Service Identities (1-2).

- **Tab 2: Characters**: List of 3 characters with summaries (Class, Magic, Professions, Playtime).

- **Tab 3: Progression**: Detailed bars/milestones for the *selected* character.

- **Tab 4: Achievements**: Full account-wide list of earned trophies and medals.

- **Tab 5: Reputation (Private)**: Ledger of balance, history, and progress to the next rank.



---



## 4. Core System 3: Rank Hierarchy (Progression)

Ranks represent a player's long-term "worth" and dedication. They are awarded automatically based on `rep_total` (Lifetime Earned).



### 4.1 The 20-Rank Ladder (Veteran Capped)

The ladder has been restructured to celebrate the "Veteran" as the highest achievement, with a scaling system that rewards long-term effort up to 1,000,000 points.



1. **Veteran**: 1,000,000 pts (Highest Achievement)
2. **Lord**: 750,000 pts
3. **Hero**: 500,000 pts
4. **Knight**: 350,000 pts
5. **Champion**: 250,000 pts
6. **Guardian**: 175,000 pts
7. **Seeker**: 125,000 pts
8. **Mystic**: 100,000 pts
9. **Adept**: 75,000 pts
10. **Historian**: 50,000 pts
11. **Scholar**: 35,000 pts
12. **Explorer**: 20,000 pts
13. **Journeyman**: 10,000 pts
14. **Apprentice**: 5,000 pts
15. **Novice**: 2,500 pts
16. **Newcomer**: 1,500 pts
17. **Regular**: 1,001 - 1,499 pts
18. **Civilian**: 501 - 1,000 pts
19. **Wanderer**: 101 - 500 pts
20. **Outcast**: 1 - 100 pts (Starting Rank)



---



## 5. Core System 4: The Jobs System (Community Bounties)

Players interact via the `/job` command to provide or request assistance.

- **Philosophy**: This is the community-driven version of the Staff Task system.

- **Job Categories & Limits**: Predetermined categories (e.g., "Boss Kill," "Resource Gathering") with fixed point limits. 

 - *Example*: Boss help = 25 Rep max.

- **Workflow**:

1. **Post**: A player posts a job (Provider) or a request (Uploader).
2. **Accept (Hire)**: Another player accepts the post.
3. **Verification**: Bot approves completion (possibly with staff spot-checks).
4. **Payout**: Reputation points applied to the provider's account.



---



## 6. Core System 5: Staff Task Management (Workspace)

"Task Points" are for staff members only. They represent creation and maintenance effort.



### 6.1 Task Creation & Points Approval

- **Ownership**: Any staff can create a task for any other staff.

- **Points Scale**:

 - **1 pt**: Basic/Easy (Single item addition, simple texture edit).

 - **10 pts**: Moderate (File config, terraforming, decent sized build).

 - **25, 50, 75 pts**: Major contribution.

 - **100 pts**: Max limit (unless Admin/Owner created).

- **Approval Logic**:

 - Suggested points are default **0**. 

 - Only **Owner** or **Admin** can approve the final point credit to avoid abuse or farming.



### 6.2 Staff Compensation (Rewards)

Task points are metrics for "Cash Bonus Tips."

- **Scale Example**: 1,000 pts = ~$20–$30 bonus.

- **Rewards**: Incremental milestones (10, 50, 100, 250, 500, etc.) ensure rewards feel meaningful as they scale.



---



## 7. Core System 6: Minecraft ↔ Discord Sync

Automated point granting based on in-game actions.



1. **Read**: Minecraft bot parses server logs for achievements (e.g., `Player X unlocked Woodcutting 5`).
2. **Match**: Discord bot finds the linked Discord ID for `Player X`.
3. **Grant**: Bot awards points based on a **Master Award List** (e.g., Achievement = 5 pts).



---



## 8. Discord Channel Layout (The Foundation)



### 8.1 📌 INFORMATION (Read-only)

- **#welcome**, **#rules**, **#server-lore**, **#how-to-play**, **#faq**, **#announcements**.



### 8.2 👤 PLAYER HUB

- **#link-account**, **#profile**, **#reputation**, **#rank-progression**, **#identity-tags**.



### 8.3 🧭 GAME SYSTEMS

- **#quests**, **#professions**, **#classes**, **#magic**, **#factions**, **#civilizations**.



### 8.4 💰 ECONOMY & REWARDS

- **#rep-shop**, **#rep-transactions**, **#purchase-confirmation**, **#reward-log**.



### 8.5 🛡 STAFF ROOM (Restricted)

- **#staff-general**, **#announcements**, **#server-ideas**, **#server-info (Forum)**, **#projects (Forum)**, **#billable-work**, **#staff-meetings**.



### 8.6 🔐 PRIVATE WORK (Highly Restricted)

- **#owner-name** (e.g., `#owner-bob`): One per staff member for private feedback.



---



## 9. Strategy & Improvement Suggestions



### 1. Point Economy Separation

Ensure "Task Points" (Staff Cash-out) and "Reputation" (Player Crates) are treated as two distinct logic paths.

- **Proposed Solution**: Staff have a `task_point_balance` used for bonuses, while players (and staff) have a `reputation_balance` used for in-game crates.



### 2. The "Hiring" Audit

For the Job system, include a `/job approve <id>` command for moderators to verify high-value jobs (e.g., those over 50 points).



---



## 10. Final Developer Rules

1. **UUID-Centric**: Every scrap of data must be tied to the Minecraft UUID.
2. **Approval State Machine**: Task points are "Pending" until an Admin/Owner approves.
3. **Transaction Integrity**: Every point change MUST have a reason string in the SQL database for audit.
4. **No Tables**: This document is list-formatted for perfect Google Docs compatibility.