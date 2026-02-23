package com.riftalstudios.qtccore.commands;

import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.profile.PlayerProfile;
import com.riftalstudios.qtccore.reputation.ReputationTransaction;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class RepCommand implements SubCommand {

    private final QTCCore plugin;
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("MM/dd HH:mm");

    public RepCommand(QTCCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "rep";
    }

    @Override
    public String getPermission() {
        return null; // Balance/history are public, grant/remove require admin
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            // Show own balance if player
            if (sender instanceof Player player) {
                showBalance(player, player.getUniqueId(), player.getName());
            } else {
                sendUsage(sender);
            }
            return;
        }

        String action = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (action) {
            case "balance" -> {
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
                    showBalance(player, target.getUniqueId(), target.getName());
                } else {
                    showBalance(player, player.getUniqueId(), player.getName());
                }
            }
            case "history" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(plugin.getMessageConfig().getMessage("errors.player-only"));
                    return;
                }
                int page = 0;
                if (subArgs.length > 0) {
                    try {
                        page = Integer.parseInt(subArgs[0]) - 1;
                        if (page < 0) page = 0;
                    } catch (NumberFormatException ignored) {}
                }
                showHistory(player, player.getUniqueId(), page);
            }
            case "grant" -> {
                if (!sender.hasPermission("qtccore.admin")) {
                    sender.sendMessage(plugin.getMessageConfig().getMessage("errors.no-permission"));
                    return;
                }
                if (subArgs.length < 3) {
                    sender.sendMessage("§cUsage: /qtc rep grant <player> <amount> <reason...>");
                    return;
                }
                handleGrant(sender, subArgs);
            }
            case "remove" -> {
                if (!sender.hasPermission("qtccore.admin")) {
                    sender.sendMessage(plugin.getMessageConfig().getMessage("errors.no-permission"));
                    return;
                }
                if (subArgs.length < 3) {
                    sender.sendMessage("§cUsage: /qtc rep remove <player> <amount> <reason...>");
                    return;
                }
                handleRemove(sender, subArgs);
            }
            default -> sendUsage(sender);
        }
    }

    private void showBalance(Player viewer, UUID uuid, String name) {
        plugin.getProfileManager().getOrLoadProfile(uuid).thenAccept(profile -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                viewer.sendMessage("§6=== Reputation: " + name + " ===");
                viewer.sendMessage("§7Balance: §f" + profile.getRepBalance());
                viewer.sendMessage("§7Total Earned: §f" + profile.getRepTotal());
                viewer.sendMessage("§7Rank: §f" + profile.getCurrentRank().getDisplayName());
            });
        });
    }

    private void showHistory(Player player, UUID uuid, int page) {
        plugin.getReputationManager().getHistory(uuid, page).thenAccept(transactions -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("§6=== Rep History (Page " + (page + 1) + ") ===");
                if (transactions.isEmpty()) {
                    player.sendMessage("§7No transactions found.");
                    return;
                }
                for (ReputationTransaction txn : transactions) {
                    String sign = txn.amount() >= 0 ? "§a+" : "§c";
                    player.sendMessage(sign + txn.amount() + " §7" + txn.reason()
                            + " §8(" + DATE_FORMAT.format(new Date(txn.createdAt())) + ")");
                }
            });
        });
    }

    private void handleGrant(CommandSender sender, String[] args) {
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found or not online.");
            return;
        }

        long amount;
        try {
            amount = Long.parseLong(args[1]);
            if (amount <= 0) {
                sender.sendMessage("§cAmount must be positive.");
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid amount.");
            return;
        }

        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        plugin.getReputationManager().addReputation(target.getUniqueId(), amount, reason, "ADMIN_GRANT",
                sender instanceof Player p ? p.getUniqueId().toString() : "CONSOLE");
        sender.sendMessage("§aGranted " + amount + " rep to " + target.getName() + ".");
        target.sendMessage("§aYou received " + amount + " reputation: " + reason);
    }

    private void handleRemove(CommandSender sender, String[] args) {
        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage("§cPlayer not found or not online.");
            return;
        }

        long amount;
        try {
            amount = Long.parseLong(args[1]);
            if (amount <= 0) {
                sender.sendMessage("§cAmount must be positive.");
                return;
            }
        } catch (NumberFormatException e) {
            sender.sendMessage("§cInvalid amount.");
            return;
        }

        String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
        plugin.getReputationManager().removeReputation(target.getUniqueId(), amount, reason, "ADMIN_REMOVE",
                sender instanceof Player p ? p.getUniqueId().toString() : "CONSOLE");
        sender.sendMessage("§aRemoved " + amount + " rep from " + target.getName() + ".");
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§6=== Rep Commands ===");
        sender.sendMessage("§7/qtc rep §8- View your reputation");
        sender.sendMessage("§7/qtc rep balance [player] §8- View reputation balance");
        sender.sendMessage("§7/qtc rep history [page] §8- View transaction history");
        if (sender.hasPermission("qtccore.admin")) {
            sender.sendMessage("§7/qtc rep grant <player> <amount> <reason> §8- Grant rep");
            sender.sendMessage("§7/qtc rep remove <player> <amount> <reason> §8- Remove rep");
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(Arrays.asList("balance", "history"));
            if (sender.hasPermission("qtccore.admin")) {
                options.add("grant");
                options.add("remove");
            }
            return filterStartsWith(options, args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("balance") || args[0].equalsIgnoreCase("grant")
                || args[0].equalsIgnoreCase("remove"))) {
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
