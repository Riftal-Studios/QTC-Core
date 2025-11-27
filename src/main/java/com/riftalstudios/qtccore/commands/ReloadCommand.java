package com.riftalstudios.qtccore.commands;

import com.riftalstudios.qtccore.QTCCore;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public class ReloadCommand implements SubCommand {

    private final QTCCore plugin;

    public ReloadCommand(QTCCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public String getPermission() {
        return "qtccore.admin";
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        plugin.getConfigManager().reload();
        plugin.getMessageConfig().reload();
        sender.sendMessage(plugin.getMessageConfig().getMessage("plugin.reload"));
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}
