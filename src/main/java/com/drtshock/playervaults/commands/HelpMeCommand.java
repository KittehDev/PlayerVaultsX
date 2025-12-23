/*
 * PlayerVaultsX
 * Copyright (C) 2013 Trent Hensler
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.drtshock.playervaults.commands;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.util.ComponentDispatcher;
import com.drtshock.playervaults.util.Permission;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.kitteh.pastegg.PasteBuilder;
import org.kitteh.pastegg.PasteContent;
import org.kitteh.pastegg.PasteFile;
import org.kitteh.pastegg.Visibility;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.Level;

public class HelpMeCommand implements CommandExecutor {
    public static final boolean likesCats = Arrays.stream(PlayerVaults.class.getDeclaredMethods()).anyMatch(m -> m.isSynthetic() && m.getName().startsWith("loadC") && m.getName().endsWith("0"));
    private final PlayerVaults plugin;

    public HelpMeCommand(PlayerVaults plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(final CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(Permission.ADMIN)) {
            this.plugin.getTL().noPerms().title().send(sender);
        } else {
            StringBuilder mainInfo = new StringBuilder();
            mainInfo.append(Bukkit.getName()).append(" version: ").append(Bukkit.getServer().getVersion()).append('\n');
            mainInfo.append("Plugin ").append(likesCats ? "version" : "Version").append(": ").append(plugin.getDescription().getVersion()).append('\n');
            mainInfo.append("Java version: ").append(System.getProperty("java.version")).append('\n');
            if (args.length >= 1 && args[0].equalsIgnoreCase("mini")) {
                for (String string : mainInfo.toString().split("\n")) {
                    ComponentDispatcher.send(sender, MiniMessage.miniMessage().deserialize((sender instanceof Player ? "<rainbow>" : "<green>") + string));
                }
                return true;
            }
            mainInfo.append('\n');
            mainInfo.append("Command run by: ").append(sender.getName()).append('\n');
            mainInfo.append('\n');
            mainInfo.append("Plugins:\n");
            for (Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
                mainInfo.append(' ').append(plugin.getName()).append(" - ").append(plugin.getDescription().getVersion()).append('\n');
                mainInfo.append("  ").append(plugin.getDescription().getAuthors()).append('\n');
            }

            PlayerVaults.scheduler().runAsync(task -> {
                try {
                    final PasteBuilder builder = new PasteBuilder().name("PlayerVaultsX Debug")
                            .visibility(Visibility.UNLISTED)
                            .expires(ZonedDateTime.now(ZoneOffset.UTC).plusDays(3));
                    final int[] i = new int[]{0};

                    final BiConsumer<String, String> add = (name, content) ->
                            builder.addFile(new PasteFile(i[0]++ + name, new PasteContent(PasteContent.ContentType.TEXT, content)));
                    final Function<Path, String> getFile = (file) -> {
                        try {
                            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            return e.getMessage();
                        }
                    };

                    Path dataPath = plugin.getDataFolder().toPath();
                    add.accept("info.txt", mainInfo.toString());
                    String exceptionLog = plugin.getExceptions();
                    if (exceptionLog != null) {
                        add.accept("exceptions.txt", exceptionLog);
                    }
                    add.accept("config.conf", getFile.apply(dataPath.resolve("config.conf")));

                    PasteBuilder.PasteResult result = builder.build();

                    PlayerVaults.scheduler().runNextTick(t -> {
                        if (result.getPaste().isPresent()) {
                            String delKey = result.getPaste().get().getDeletionKey().orElse("No deletion key");
                            String url = "https://paste.gg/anonymous/" + result.getPaste().get().getId();
                            ComponentDispatcher.send(sender, Component.text("URL generated: ").append(Component.text().clickEvent(ClickEvent.openUrl(url)).content(url)));
                            ComponentDispatcher.send(sender, MiniMessage.miniMessage().deserialize((sender instanceof Player ? "<rainbow>" : "<green>") + "Deletion key:</rainbow> " + delKey));
                        } else {
                            ComponentDispatcher.send(sender, MiniMessage.miniMessage().deserialize("<red>Failed to generate output. See console for details."));
                            PlayerVaults.getInstance().getLogger().warning("Received: " + result.getMessage());
                        }
                    });
                } catch (Exception e) {
                    PlayerVaults.getInstance().getLogger().log(Level.SEVERE, "Failed to execute debug command", e);
                    PlayerVaults.scheduler().runNextTick(t ->
                            ComponentDispatcher.send(sender, MiniMessage.miniMessage().deserialize("<red>Failed to generate output. See console for details."))
                    );
                }
            });
        }
        return true;
    }
}
