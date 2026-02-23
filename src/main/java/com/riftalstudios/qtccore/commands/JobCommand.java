package com.riftalstudios.qtccore.commands;

import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.jobs.Job;
import com.riftalstudios.qtccore.jobs.JobCategory;
import com.riftalstudios.qtccore.jobs.JobStatus;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class JobCommand implements SubCommand {

    private final QTCCore plugin;

    public JobCommand(QTCCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "job";
    }

    @Override
    public String getPermission() {
        return null;
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
            case "post" -> handlePost(player, subArgs);
            case "list" -> handleList(player, subArgs);
            case "accept" -> handleAccept(player, subArgs);
            case "complete" -> handleComplete(player, subArgs);
            case "verify" -> handleVerify(player, subArgs);
            case "cancel" -> handleCancel(player, subArgs);
            case "info" -> handleInfo(player, subArgs);
            default -> sendUsage(player);
        }
    }

    private void handlePost(Player player, String[] args) {
        // /qtc job post <category> <points> <description...>
        if (args.length < 3) {
            player.sendMessage("§cUsage: /qtc job post <category> <points> <description...>");
            player.sendMessage("§7Categories: " + Arrays.stream(JobCategory.values())
                    .map(c -> c.name().toLowerCase()).collect(Collectors.joining(", ")));
            return;
        }

        JobCategory category;
        try {
            category = JobCategory.valueOf(args[0].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage("§cInvalid category: " + args[0]);
            return;
        }

        int points;
        try {
            points = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            player.sendMessage("§cInvalid points: " + args[1]);
            return;
        }

        if (points > category.getMaxPoints()) {
            player.sendMessage("§cMax points for " + category.name() + " is " + category.getMaxPoints() + ".");
            return;
        }

        String description = String.join(" ", Arrays.copyOfRange(args, 2, args.length));

        plugin.getJobManager().postJob(player.getUniqueId(), category, description, points).thenAccept(job -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.sendMessage("§aJob #" + job.getId() + " posted! Category: " + category.name()
                        + ", Points: " + job.getPointsOffered());
            });
        });
    }

    private void handleList(Player player, String[] args) {
        if (args.length > 0) {
            // Filter by category
            try {
                JobCategory category = JobCategory.valueOf(args[0].toUpperCase());
                plugin.getJobManager().getJobsByCategory(category).thenAccept(jobs -> {
                    showJobList(player, jobs, category.name() + " Jobs");
                });
            } catch (IllegalArgumentException e) {
                player.sendMessage("§cInvalid category: " + args[0]);
            }
        } else {
            plugin.getJobManager().getOpenJobs().thenAccept(jobs -> {
                showJobList(player, jobs, "Open Jobs");
            });
        }
    }

    private void showJobList(Player player, List<Job> jobs, String title) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            player.sendMessage("§6=== " + title + " (" + jobs.size() + ") ===");
            if (jobs.isEmpty()) {
                player.sendMessage("§7No jobs found.");
                return;
            }
            int count = 0;
            for (Job job : jobs) {
                if (count >= 10) {
                    player.sendMessage("§7... and " + (jobs.size() - 10) + " more");
                    break;
                }
                player.sendMessage("§7#" + job.getId() + " §f" + job.getCategory().name()
                        + " §7(" + job.getPointsOffered() + " pts) §8" + truncate(job.getDescription(), 30));
                count++;
            }
        });
    }

    private void handleAccept(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage("§cUsage: /qtc job accept <id>");
            return;
        }

        long jobId = parseId(args[0], player);
        if (jobId < 0) return;

        plugin.getJobManager().acceptJob(jobId, player.getUniqueId()).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.sendMessage("§aAccepted job #" + jobId + ".");
                } else {
                    player.sendMessage("§cCould not accept job. It may not be open or you are the poster.");
                }
            });
        });
    }

    private void handleComplete(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage("§cUsage: /qtc job complete <id>");
            return;
        }

        long jobId = parseId(args[0], player);
        if (jobId < 0) return;

        plugin.getJobManager().completeJob(jobId, player.getUniqueId()).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.sendMessage("§aJob #" + jobId + " marked as completed.");
                } else {
                    player.sendMessage("§cCould not complete job. Check job status and ownership.");
                }
            });
        });
    }

    private void handleVerify(Player player, String[] args) {
        if (!player.hasPermission("qtccore.staff")) {
            player.sendMessage(plugin.getMessageConfig().getMessage("errors.no-permission"));
            return;
        }

        if (args.length == 0) {
            player.sendMessage("§cUsage: /qtc job verify <id>");
            return;
        }

        long jobId = parseId(args[0], player);
        if (jobId < 0) return;

        plugin.getJobManager().verifyJob(jobId, player.getUniqueId()).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.sendMessage("§aJob #" + jobId + " verified. Reputation awarded.");
                } else {
                    player.sendMessage("§cCould not verify job. It may not be in COMPLETED status.");
                }
            });
        });
    }

    private void handleCancel(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage("§cUsage: /qtc job cancel <id>");
            return;
        }

        long jobId = parseId(args[0], player);
        if (jobId < 0) return;

        boolean isStaff = player.hasPermission("qtccore.staff");
        plugin.getJobManager().cancelJob(jobId, player.getUniqueId(), isStaff).thenAccept(success -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (success) {
                    player.sendMessage("§aJob #" + jobId + " cancelled.");
                } else {
                    player.sendMessage("§cCould not cancel job. Check permissions and job status.");
                }
            });
        });
    }

    private void handleInfo(Player player, String[] args) {
        if (args.length == 0) {
            player.sendMessage("§cUsage: /qtc job info <id>");
            return;
        }

        long jobId = parseId(args[0], player);
        if (jobId < 0) return;

        plugin.getJobManager().getJob(jobId).thenAccept(job -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (job == null) {
                    player.sendMessage("§cJob not found.");
                    return;
                }
                player.sendMessage("§6=== Job #" + job.getId() + " ===");
                player.sendMessage("§7Category: §f" + job.getCategory().name());
                player.sendMessage("§7Description: §f" + job.getDescription());
                player.sendMessage("§7Points: §f" + job.getPointsOffered());
                player.sendMessage("§7Status: §f" + job.getStatus().name());
                player.sendMessage("§7Poster: §f" + resolveName(job.getPosterUuid()));
                if (job.getAccepterUuid() != null) {
                    player.sendMessage("§7Accepter: §f" + resolveName(job.getAccepterUuid()));
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

    private String truncate(String text, int max) {
        return text.length() > max ? text.substring(0, max) + "..." : text;
    }

    private void sendUsage(Player player) {
        player.sendMessage("§6=== Job Commands ===");
        player.sendMessage("§7/qtc job post <category> <points> <desc> §8- Post a job");
        player.sendMessage("§7/qtc job list [category] §8- List open jobs");
        player.sendMessage("§7/qtc job accept <id> §8- Accept a job");
        player.sendMessage("§7/qtc job complete <id> §8- Mark as completed");
        player.sendMessage("§7/qtc job verify <id> §8- Verify a completed job (staff)");
        player.sendMessage("§7/qtc job cancel <id> §8- Cancel a job");
        player.sendMessage("§7/qtc job info <id> §8- View job details");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return filterStartsWith(Arrays.asList("post", "list", "accept", "complete", "verify", "cancel", "info"), args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("post") || args[0].equalsIgnoreCase("list")) {
                return filterStartsWith(
                        Arrays.stream(JobCategory.values()).map(c -> c.name().toLowerCase()).toList(),
                        args[1]
                );
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
