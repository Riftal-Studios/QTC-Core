package com.riftalstudios.qtccore.commands;

import com.riftalstudios.qtccore.QTCCore;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QTCCommand implements CommandExecutor, TabCompleter {

    private final QTCCore plugin;
    private final Map<String, SubCommand> subCommands = new HashMap<>();

    public QTCCommand(QTCCore plugin) {
        this.plugin = plugin;
        registerSubCommand(new ReloadCommand(plugin));
        registerSubCommand(new NodeCommand(plugin));
        registerSubCommand(new LinkCommand(plugin));
    }

    public void registerSubCommand(SubCommand subCommand) {
        subCommands.put(subCommand.getName().toLowerCase(), subCommand);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommandName = args[0].toLowerCase();
        SubCommand subCommand = subCommands.get(subCommandName);

        if (subCommand == null) {
            sender.sendMessage(plugin.getMessageConfig().getMessage("errors.invalid-command"));
            return true;
        }

        if (subCommand.getPermission() != null && !sender.hasPermission(subCommand.getPermission())) {
            sender.sendMessage(plugin.getMessageConfig().getMessage("errors.no-permission"));
            return true;
        }

        String[] subArgs = new String[args.length - 1];
        System.arraycopy(args, 1, subArgs, 0, args.length - 1);
        subCommand.execute(sender, subArgs);
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                 @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String partial = args[0].toLowerCase();
            for (SubCommand subCommand : subCommands.values()) {
                if (subCommand.getName().toLowerCase().startsWith(partial)) {
                    if (subCommand.getPermission() == null || sender.hasPermission(subCommand.getPermission())) {
                        completions.add(subCommand.getName());
                    }
                }
            }
            return completions;
        }

        if (args.length > 1) {
            SubCommand subCommand = subCommands.get(args[0].toLowerCase());
            if (subCommand != null) {
                String[] subArgs = new String[args.length - 1];
                System.arraycopy(args, 1, subArgs, 0, args.length - 1);
                return subCommand.tabComplete(sender, subArgs);
            }
        }

        return new ArrayList<>();
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6=== QTC Core Commands ===");
        for (SubCommand subCommand : subCommands.values()) {
            if (subCommand.getPermission() == null || sender.hasPermission(subCommand.getPermission())) {
                sender.sendMessage("§7/qtc " + subCommand.getName());
            }
        }
    }
}
