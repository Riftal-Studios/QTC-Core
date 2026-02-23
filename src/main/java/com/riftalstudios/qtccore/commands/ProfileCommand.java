package com.riftalstudios.qtccore.commands;

import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.profile.Badge;
import com.riftalstudios.qtccore.profile.PlayerProfile;
import com.riftalstudios.qtccore.profile.ServiceIdentity;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class ProfileCommand implements SubCommand {

    private final QTCCore plugin;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");

    public ProfileCommand(QTCCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "profile";
    }

    @Override
    public String getPermission() {
        return null; // All players can use this
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageConfig().getMessage("errors.player-only"));
            return;
        }

        if (args.length == 0) {
            showProfile(player, player.getUniqueId(), player.getName());
            return;
        }

        String action = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (action) {
            case "view" -> {
                if (subArgs.length == 0) {
                    showProfile(player, player.getUniqueId(), player.getName());
                } else {
                    Player target = Bukkit.getPlayer(subArgs[0]);
                    if (target == null) {
                        player.sendMessage("§cPlayer not found or not online.");
                        return;
                    }
                    showProfile(player, target.getUniqueId(), target.getName());
                }
            }
            case "badge" -> handleBadge(player, subArgs);
            case "identity" -> handleIdentity(player, subArgs);
            default -> sendUsage(player);
        }
    }

    private void showProfile(Player viewer, UUID targetUuid, String targetName) {
        plugin.getProfileManager().getOrLoadProfile(targetUuid).thenAccept(profile -> {
            if (profile == null) {
                viewer.sendMessage("§cProfile not found.");
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                viewer.sendMessage("§6=== Profile: " + targetName + " ===");
                viewer.sendMessage("§7Rank: §f" + profile.getCurrentRank().getDisplayName());
                viewer.sendMessage("§7Rep Balance: §f" + profile.getRepBalance());
                viewer.sendMessage("§7Rep Total: §f" + profile.getRepTotal());

                if (profile.isStaff()) {
                    viewer.sendMessage("§7Task Points: §f" + profile.getTaskPointBalance()
                            + " §8(Total: " + profile.getTaskPointTotal() + ")");
                }

                if (profile.getDiscordId() != null) {
                    viewer.sendMessage("§7Discord: §fLinked");
                }

                // Badges
                List<Badge> badges = profile.getSelectedBadges();
                if (!badges.isEmpty()) {
                    String badgeStr = badges.stream().map(Badge::name).collect(Collectors.joining(", "));
                    viewer.sendMessage("§7Badges: §f" + badgeStr);
                }

                // Identities
                List<ServiceIdentity> identities = profile.getSelectedIdentities();
                if (!identities.isEmpty()) {
                    String idStr = identities.stream().map(ServiceIdentity::name).collect(Collectors.joining(", "));
                    viewer.sendMessage("§7Identities: §f" + idStr);
                }

                viewer.sendMessage("§7Last Active: §f" + DATE_FORMAT.format(new Date(profile.getLastActive())));
                viewer.sendMessage("§7Member Since: §f" + DATE_FORMAT.format(new Date(profile.getCreatedAt())));
            });
        });
    }

    private void handleBadge(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage("§cUsage: /qtc profile badge <select|remove|list> [badge_id]");
            return;
        }

        String action = args[0].toLowerCase();
        UUID uuid = player.getUniqueId();

        switch (action) {
            case "list" -> {
                plugin.getProfileManager().getStorage().loadAllBadges(uuid).thenAccept(badges -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (badges.isEmpty()) {
                            player.sendMessage("§7You have no badges.");
                            return;
                        }
                        player.sendMessage("§6=== Your Badges ===");
                        for (Badge badge : badges) {
                            player.sendMessage("§7- §f" + badge.id() + " §8(" + badge.name() + ")");
                        }
                    });
                });
            }
            case "select" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /qtc profile badge select <badge_id>");
                    return;
                }
                String badgeId = args[1];
                plugin.getProfileManager().getStorage().loadAllBadges(uuid).thenAccept(allBadges -> {
                    Badge found = allBadges.stream().filter(b -> b.id().equals(badgeId)).findFirst().orElse(null);
                    if (found == null) {
                        player.sendMessage("§cYou don't have badge '" + badgeId + "'.");
                        return;
                    }

                    PlayerProfile profile = plugin.getProfileManager().getCachedProfile(uuid);
                    if (profile == null) return;

                    List<Badge> selected = new ArrayList<>(profile.getSelectedBadges());
                    if (selected.size() >= 3) {
                        player.sendMessage("§cYou can only select up to 3 badges. Remove one first.");
                        return;
                    }
                    if (selected.stream().anyMatch(b -> b.id().equals(badgeId))) {
                        player.sendMessage("§cBadge already selected.");
                        return;
                    }
                    selected.add(found);
                    profile.setSelectedBadges(selected);
                    plugin.getProfileManager().saveProfile(profile);
                    player.sendMessage("§aSelected badge: " + found.name());
                });
            }
            case "remove" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /qtc profile badge remove <badge_id>");
                    return;
                }
                String badgeId = args[1];
                PlayerProfile profile = plugin.getProfileManager().getCachedProfile(uuid);
                if (profile == null) return;

                List<Badge> selected = new ArrayList<>(profile.getSelectedBadges());
                boolean removed = selected.removeIf(b -> b.id().equals(badgeId));
                if (!removed) {
                    player.sendMessage("§cBadge '" + badgeId + "' is not selected.");
                    return;
                }
                profile.setSelectedBadges(selected);
                plugin.getProfileManager().saveProfile(profile);
                player.sendMessage("§aRemoved badge from profile.");
            }
            default -> player.sendMessage("§cUsage: /qtc profile badge <select|remove|list> [badge_id]");
        }
    }

    private void handleIdentity(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage("§cUsage: /qtc profile identity <select|remove|list> [identity_id]");
            return;
        }

        String action = args[0].toLowerCase();
        UUID uuid = player.getUniqueId();

        switch (action) {
            case "list" -> {
                plugin.getProfileManager().getStorage().loadAllIdentities(uuid).thenAccept(identities -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (identities.isEmpty()) {
                            player.sendMessage("§7You have no identities.");
                            return;
                        }
                        player.sendMessage("§6=== Your Identities ===");
                        for (ServiceIdentity identity : identities) {
                            player.sendMessage("§7- §f" + identity.id() + " §8(" + identity.name() + ")");
                        }
                    });
                });
            }
            case "select" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /qtc profile identity select <identity_id>");
                    return;
                }
                String identityId = args[1];
                plugin.getProfileManager().getStorage().loadAllIdentities(uuid).thenAccept(allIdentities -> {
                    ServiceIdentity found = allIdentities.stream()
                            .filter(si -> si.id().equals(identityId)).findFirst().orElse(null);
                    if (found == null) {
                        player.sendMessage("§cYou don't have identity '" + identityId + "'.");
                        return;
                    }

                    PlayerProfile profile = plugin.getProfileManager().getCachedProfile(uuid);
                    if (profile == null) return;

                    List<ServiceIdentity> selected = new ArrayList<>(profile.getSelectedIdentities());
                    if (selected.size() >= 2) {
                        player.sendMessage("§cYou can only select up to 2 identities. Remove one first.");
                        return;
                    }
                    if (selected.stream().anyMatch(si -> si.id().equals(identityId))) {
                        player.sendMessage("§cIdentity already selected.");
                        return;
                    }
                    selected.add(found);
                    profile.setSelectedIdentities(selected);
                    plugin.getProfileManager().saveProfile(profile);
                    player.sendMessage("§aSelected identity: " + found.name());
                });
            }
            case "remove" -> {
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /qtc profile identity remove <identity_id>");
                    return;
                }
                String identityId = args[1];
                PlayerProfile profile = plugin.getProfileManager().getCachedProfile(uuid);
                if (profile == null) return;

                List<ServiceIdentity> selected = new ArrayList<>(profile.getSelectedIdentities());
                boolean removed = selected.removeIf(si -> si.id().equals(identityId));
                if (!removed) {
                    player.sendMessage("§cIdentity '" + identityId + "' is not selected.");
                    return;
                }
                profile.setSelectedIdentities(selected);
                plugin.getProfileManager().saveProfile(profile);
                player.sendMessage("§aRemoved identity from profile.");
            }
            default -> player.sendMessage("§cUsage: /qtc profile identity <select|remove|list> [identity_id]");
        }
    }

    private void sendUsage(Player player) {
        player.sendMessage("§6=== Profile Commands ===");
        player.sendMessage("§7/qtc profile §8- View your profile");
        player.sendMessage("§7/qtc profile view [player] §8- View a player's profile");
        player.sendMessage("§7/qtc profile badge <select|remove|list> §8- Manage badges");
        player.sendMessage("§7/qtc profile identity <select|remove|list> §8- Manage identities");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filterStartsWith(Arrays.asList("view", "badge", "identity"), args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("view")) {
                return null; // Default to online player names
            }
            if (args[0].equalsIgnoreCase("badge") || args[0].equalsIgnoreCase("identity")) {
                return filterStartsWith(Arrays.asList("select", "remove", "list"), args[1]);
            }
        }
        return new ArrayList<>();
    }

    private List<String> filterStartsWith(List<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}
