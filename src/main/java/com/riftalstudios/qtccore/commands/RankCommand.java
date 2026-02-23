package com.riftalstudios.qtccore.commands;

import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.rank.Rank;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RankCommand implements SubCommand {

    private final QTCCore plugin;

    public RankCommand(QTCCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "rank";
    }

    @Override
    public String getPermission() {
        return null;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            // Show own rank
            if (sender instanceof Player player) {
                showRankInfo(player, player.getUniqueId(), player.getName());
            } else {
                sendUsage(sender);
            }
            return;
        }

        String action = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (action) {
            case "info" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.getMessageConfig().getMessage("errors.player-only"));
                    return;
                }
                if (subArgs.length > 0) {
                    Player target = Bukkit.getPlayer(subArgs[0]);
                    if (target == null) {
                        player.sendMessage("§cPlayer not found or not online.");
                        return;
                    }
                    showRankInfo(player, target.getUniqueId(), target.getName());
                } else {
                    showRankInfo(player, player.getUniqueId(), player.getName());
                }
            }
            case "list" -> showRankList(sender);
            case "recalculate" -> {
                if (!sender.hasPermission("qtccore.admin")) {
                    sender.sendMessage(plugin.getMessageConfig().getMessage("errors.no-permission"));
                    return;
                }
                if (subArgs.length == 0) {
                    sender.sendMessage("§cUsage: /qtc rank recalculate <player>");
                    return;
                }
                Player target = Bukkit.getPlayer(subArgs[0]);
                if (target == null) {
                    sender.sendMessage("§cPlayer not found or not online.");
                    return;
                }
                Rank newRank = plugin.getRankManager().recalculate(target.getUniqueId());
                sender.sendMessage("§aRecalculated rank for " + target.getName() + ": " + newRank.getDisplayName());
            }
            default -> sendUsage(sender);
        }
    }

    private void showRankInfo(Player viewer, java.util.UUID uuid, String name) {
        plugin.getProfileManager().getOrLoadProfile(uuid).thenAccept(profile -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Rank current = profile.getCurrentRank();
                viewer.sendMessage("§6=== Rank: " + name + " ===");
                viewer.sendMessage("§7Current Rank: §f" + current.getDisplayName());
                viewer.sendMessage("§7Rep Total: §f" + profile.getRepTotal());

                // Find next rank
                Rank next = null;
                for (int i = current.ordinal() - 1; i >= 0; i--) {
                    next = Rank.values()[i];
                    break;
                }
                if (next != null) {
                    long needed = next.getThreshold() - profile.getRepTotal();
                    viewer.sendMessage("§7Next Rank: §f" + next.getDisplayName()
                            + " §8(" + needed + " rep needed)");
                } else {
                    viewer.sendMessage("§7§oMax rank achieved!");
                }
            });
        });
    }

    private void showRankList(CommandSender sender) {
        sender.sendMessage("§6=== Rank Ladder ===");
        for (Rank rank : Rank.values()) {
            sender.sendMessage("§7" + rank.getDisplayName() + " §8- " + String.format("%,d", rank.getThreshold()) + " pts");
        }
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§6=== Rank Commands ===");
        sender.sendMessage("§7/qtc rank §8- View your rank");
        sender.sendMessage("§7/qtc rank info [player] §8- View rank info");
        sender.sendMessage("§7/qtc rank list §8- Show all ranks");
        if (sender.hasPermission("qtccore.admin")) {
            sender.sendMessage("§7/qtc rank recalculate <player> §8- Recalculate rank");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(Arrays.asList("info", "list"));
            if (sender.hasPermission("qtccore.admin")) {
                options.add("recalculate");
            }
            return filterStartsWith(options, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("info") || args[0].equalsIgnoreCase("recalculate"))) {
            return null; // Online player names
        }
        return new ArrayList<>();
    }

    private List<String> filterStartsWith(List<String> options, String prefix) {
        return options.stream()
                .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                .collect(Collectors.toList());
    }
}
