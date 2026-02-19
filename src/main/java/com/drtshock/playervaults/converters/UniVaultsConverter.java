package com.drtshock.playervaults.converters;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.vaultmanagement.VaultManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class UniVaultsConverter implements Converter {
    @Override
    public int run(CommandSender initiator) {
        PlayerVaults plugin = PlayerVaults.getInstance();
        VaultManager vaultManager = VaultManager.getInstance();

        AtomicInteger counter = new AtomicInteger(0);

        Path vaultsPath = PlayerVaults.getInstance().getDataFolder().toPath().resolve("vaults");
        if (Files.exists(vaultsPath)) {
            Pattern pattern = Pattern.compile("([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})-vault-(\\d+)");
            try (Stream<Path> stream = Files.list(vaultsPath)) {
                stream.forEach(p -> {
                    Matcher matcher = pattern.matcher(p.getFileName().toString());
                    if (!matcher.find()) {
                        return;
                    }
                    String uuid = matcher.group(1);
                    int id = Integer.parseInt(matcher.group(2));
                    try {
                        String b64 = Files.readString(p);
                        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(Base64.getMimeDecoder().decode(b64));
                        try (BukkitObjectInputStream bukkitObjectInputStream = new BukkitObjectInputStream(byteArrayInputStream)) {
                            ItemStack[] items = (ItemStack[]) bukkitObjectInputStream.readObject();
                            boolean cursed = true;
                            if (items[items.length - 9] == null || !(items[items.length - 9].getType() == Material.PAPER || items[items.length - 9].getType() == Material.GRAY_STAINED_GLASS_PANE)) {
                                cursed = false;
                            }
                            for (int i = items.length - 8; i < items.length - 1; i++) {
                                if (items[i] == null || !(items[i].getType() == Material.GRAY_STAINED_GLASS_PANE)) {
                                    cursed = false;
                                }
                            }
                            if (items[items.length - 1] == null || !(items[items.length - 1].getType() == Material.PAPER || items[items.length - 1].getType() == Material.GRAY_STAINED_GLASS_PANE)) {
                                cursed = false;
                            }
                            if (cursed) {
                                items = Arrays.copyOfRange(items, 0, items.length - 9);
                            }
                            Inventory inventory = Bukkit.createInventory(null, items.length % 9 == 0 ? items.length : (6 * 9), "Converting!");
                            inventory.setContents(items);
                            vaultManager.saveVault(inventory, uuid, id);
                            counter.incrementAndGet();
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to read vault " + p.getFileName(), e);
                    }
                });
            } catch (IOException ignored) {
            }
            try {
                Files.move(vaultsPath, PlayerVaults.getInstance().getDataFolder().toPath().resolve("oldUniVaults"));
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to move old UniVaults file", e);
            }
        }

        return counter.get();
    }

    @Override
    public boolean canConvert() {
        Path vaultsPath = PlayerVaults.getInstance().getDataFolder().toPath().resolve("vaults");
        if (Files.exists(vaultsPath)) {
            try (Stream<Path> stream = Files.list(vaultsPath)) {
                return stream.anyMatch(p -> p.getFileName().toString().matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}-vault-\\d+"));
            } catch (IOException ignored) {
            }
        }
        return false;
    }

    @Override
    public String getName() {
        return "UniVaults";
    }
}
