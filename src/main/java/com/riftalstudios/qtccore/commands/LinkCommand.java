package com.riftalstudios.qtccore.commands;

import com.riftalstudios.qtccore.QTCCore;
import com.riftalstudios.qtccore.api.DiscordAPIClient;
import com.riftalstudios.qtccore.api.DiscordAPIResponse.LinkCodeResponse;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class LinkCommand implements SubCommand {

    private final QTCCore plugin;

    public LinkCommand(QTCCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "link";
    }

    @Override
    public String getPermission() {
        return null;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cThis command can only be used by players.");
            return;
        }

        DiscordAPIClient client = plugin.getDiscordAPIManager().getClient();
        if (client == null) {
            player.sendMessage("§cDiscord integration is not enabled.");
            return;
        }

        String playerUuid = player.getUniqueId().toString();
        String playerName = player.getName();

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                LinkCodeResponse response = client.requestLinkCode(playerUuid, playerName).join();
                if (response == null) {
                    player.sendMessage("§cCould not reach the Discord bot. Please try again later.");
                    return;
                }
                if (response.success()) {
                    player.sendMessage("§aYour link code is: §e§l" + response.code());
                    player.sendMessage("§aType §e/link " + response.code() + " §ain Discord within 5 minutes.");
                } else {
                    player.sendMessage("§c" + response.message());
                }
            } catch (Exception e) {
                player.sendMessage("§cCould not reach the Discord bot. Please try again later.");
                plugin.getLogger().warning("Link code request failed for " + playerName + ": " + e.getMessage());
            }
        });
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        return new ArrayList<>();
    }
}
