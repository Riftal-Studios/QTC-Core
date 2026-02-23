package com.riftalstudios.qtccore.commands;

import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.tasks.StaffTask;
import com.riftalstudios.qtccore.tasks.TaskStatus;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TaskCommand implements SubCommand {

    private final QTCCore plugin;

    public TaskCommand(QTCCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "task";
    }

    @Override
    public String getPermission() {
        return "qtccore.staff";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(plugin.getMessageConfig().getMessage("errors.player-only"));
            return;
        }

        if (args.length == 0) {
            sendUsage(player);
            return;
        }

        String action = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (action) {
            case "create" -> handleCreate(player, subArgs);
            case "list" -> handleList(player, subArgs);
            case "assign" -> handleAssign(player, subArgs);
            case "start" -> handleStart(player, subArgs);
            case "submit" -> handleSubmit(player, subArgs);
            case "approve" -> handleApprove(player, subArgs);
            case "reject" -> handleReject(player, subArgs);
            case "info" -> handleInfo(player, subArgs);
            default -> sendUsage(player);
        }
    }

    private void handleCreate(Player player, String[] args) {
        // /qtc task create <assignee> <points> <title...>
        if (args.length < 3) {
            player.sendMessage("§cUsage: /qtc task create <assignee> <points> <title...>");
            return;
        }

        Player assignee = Bukkit.getPlayer(args[0]);
        if (assignee == null) {
            player.sendMessage("§cPlayer not found or not online.");
            return;
        }

        int points;
        try {
            points = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid points: " + args[1]);
            return;
        }

        String title = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        plugin.getStaffTaskManager().createTask(title, "", player.getUniqueId(), assignee.getUniqueId(), points)
                .thenAccept(task -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        player.sendMessage("§aTask #" + task.getId() + " created and assigned to " + assignee.getName() + ".");
                        assignee.sendMessage("§aYou've been assigned task #" + task.getId() + ": " + title);
                    });
                });
    }

    private void handleList(Player player, String[] args) {
        if (args.length > 0) {
            try {
                TaskStatus status = TaskStatus.valueOf(args[0].toUpperCase());
                plugin.getStaffTaskManager().getTasksByStatus(status).thenAccept(tasks -> {
                    showTaskList(player, tasks, status.name() + " Tasks");
                });
            } catch (IllegalArgumentException e) {
                player.sendMessage("§cInvalid status: " + args[0]);
                player.sendMessage("§7Valid: " + Arrays.stream(TaskStatus.values())
                        .map(s -> s.name().toLowerCase()).collect(Collectors.joining(", ")));
            }
        } else {
            // Show assigned tasks for this player
            plugin.getStaffTaskManager().getTasksByAssignee(player.getUniqueId()).thenAccept(tasks -> {
                showTaskList(player, tasks, "Your Tasks");
            });
        }
    }

    private void showTaskList(Player player, List<StaffTask> tasks, String title) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.sendMessage("§6=== " + title + " (" + tasks.size() + ") ===");
            if (tasks.isEmpty()) {
                player.sendMessage("§7No tasks found.");
                return;
            }
            for (StaffTask task : tasks) {
                player.sendMessage("§7#" + task.getId() + " §f" + task.getTitle()
                        + " §8[" + task.getStatus().name() + "] (" + task.getSuggestedPoints() + " pts)");
            }
        });
    }

    private void handleAssign(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§cUsage: /qtc task assign <id> <player>");
            return;
        }

        long taskId = parseId(args[0], player);
        if (taskId < 0) return;

        Player assignee = Bukkit.getPlayer(args[1]);
        if (assignee == null) {
            player.sendMessage("§cPlayer not found or not online.");
            return;
        }

        plugin.getStaffTaskManager().assignTask(taskId, assignee.getUniqueId()).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.sendMessage("§aTask #" + taskId + " assigned to " + assignee.getName() + ".");
                    assignee.sendMessage("§aYou've been assigned task #" + taskId + ".");
                } else {
                    player.sendMessage("§cCould not assign task. Check task status.");
                }
            });
        });
    }

    private void handleStart(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage("§cUsage: /qtc task start <id>");
            return;
        }

        long taskId = parseId(args[0], player);
        if (taskId < 0) return;

        plugin.getStaffTaskManager().startTask(taskId, player.getUniqueId()).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.sendMessage("§aStarted task #" + taskId + ".");
                } else {
                    player.sendMessage("§cCould not start task. You must be the assignee and task must be ASSIGNED.");
                }
            });
        });
    }

    private void handleSubmit(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage("§cUsage: /qtc task submit <id>");
            return;
        }

        long taskId = parseId(args[0], player);
        if (taskId < 0) return;

        plugin.getStaffTaskManager().submitTask(taskId, player.getUniqueId()).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.sendMessage("§aTask #" + taskId + " submitted for review.");
                } else {
                    player.sendMessage("§cCould not submit task. Must be IN_PROGRESS and you must be the assignee.");
                }
            });
        });
    }

    private void handleApprove(Player player, String[] args) {
        if (!player.hasPermission("qtccore.admin")) {
            player.sendMessage(plugin.getMessageConfig().getMessage("errors.no-permission"));
            return;
        }

        if (args.length == 0) {
            player.sendMessage("§cUsage: /qtc task approve <id> [points]");
            return;
        }

        long taskId = parseId(args[0], player);
        if (taskId < 0) return;

        // If no points specified, use suggested points
        if (args.length < 2) {
            plugin.getStaffTaskManager().getTask(taskId).thenAccept(task -> {
                if (task == null) {
                    player.sendMessage("§cTask not found.");
                    return;
                }
                doApprove(player, taskId, task.getSuggestedPoints());
            });
        } else {
            int points;
            try {
                points = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid points: " + args[1]);
                return;
            }
            doApprove(player, taskId, points);
        }
    }

    private void doApprove(Player player, long taskId, int points) {
        plugin.getStaffTaskManager().approveTask(taskId, player.getUniqueId(), points).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.sendMessage("§aTask #" + taskId + " approved with " + points + " task points.");
                } else {
                    player.sendMessage("§cCould not approve task. Must be in SUBMITTED status.");
                }
            });
        });
    }

    private void handleReject(Player player, String[] args) {
        if (!player.hasPermission("qtccore.admin")) {
            player.sendMessage(plugin.getMessageConfig().getMessage("errors.no-permission"));
            return;
        }

        if (args.length < 2) {
            player.sendMessage("§cUsage: /qtc task reject <id> <reason...>");
            return;
        }

        long taskId = parseId(args[0], player);
        if (taskId < 0) return;

        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

        plugin.getStaffTaskManager().rejectTask(taskId, player.getUniqueId(), reason).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.sendMessage("§aTask #" + taskId + " rejected.");
                } else {
                    player.sendMessage("§cCould not reject task. Must be in SUBMITTED status.");
                }
            });
        });
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage("§cUsage: /qtc task info <id>");
            return;
        }

        long taskId = parseId(args[0], player);
        if (taskId < 0) return;

        plugin.getStaffTaskManager().getTask(taskId).thenAccept(task -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (task == null) {
                    player.sendMessage("§cTask not found.");
                    return;
                }
                player.sendMessage("§6=== Task #" + task.getId() + " ===");
                player.sendMessage("§7Title: §f" + task.getTitle());
                if (task.getDescription() != null && !task.getDescription().isEmpty()) {
                    player.sendMessage("§7Description: §f" + task.getDescription());
                }
                player.sendMessage("§7Status: §f" + task.getStatus().name());
                player.sendMessage("§7Suggested Points: §f" + task.getSuggestedPoints());
                if (task.getApprovedPoints() > 0) {
                    player.sendMessage("§7Approved Points: §f" + task.getApprovedPoints());
                }
                player.sendMessage("§7Creator: §f" + resolveName(task.getCreatorUuid()));
                if (task.getAssigneeUuid() != null) {
                    player.sendMessage("§7Assignee: §f" + resolveName(task.getAssigneeUuid()));
                }
            });
        });
    }

    private long parseId(String input, Player player) {
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid ID: " + input);
            return -1;
        }
    }

    private String resolveName(java.util.UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        return p != null ? p.getName() : uuid.toString().substring(0, 8);
    }

    private void sendUsage(Player player) {
        player.sendMessage("§6=== Task Commands ===");
        player.sendMessage("§7/qtc task create <assignee> <points> <title> §8- Create task");
        player.sendMessage("§7/qtc task list [status] §8- List tasks");
        player.sendMessage("§7/qtc task assign <id> <player> §8- Assign task");
        player.sendMessage("§7/qtc task start <id> §8- Start working");
        player.sendMessage("§7/qtc task submit <id> §8- Submit for review");
        if (player.hasPermission("qtccore.admin")) {
            player.sendMessage("§7/qtc task approve <id> [pts] §8- Approve task");
            player.sendMessage("§7/qtc task reject <id> <reason> §8- Reject task");
        }
        player.sendMessage("§7/qtc task info <id> §8- View details");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(Arrays.asList("create", "list", "assign", "start", "submit", "info"));
            if (sender.hasPermission("qtccore.admin")) {
                options.add("approve");
                options.add("reject");
            }
            return filterStartsWith(options, args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("create") || args[0].equalsIgnoreCase("assign")) {
                return null; // Online player names
            }
            if (args[0].equalsIgnoreCase("list")) {
                return filterStartsWith(
                        Arrays.stream(TaskStatus.values()).map(s -> s.name().toLowerCase()).toList(),
                        args[1]
                );
            }
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("assign")) {
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
