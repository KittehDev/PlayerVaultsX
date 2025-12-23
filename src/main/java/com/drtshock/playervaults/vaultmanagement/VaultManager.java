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

package com.drtshock.playervaults.vaultmanagement;

import com.drtshock.playervaults.PlayerVaults;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import static com.drtshock.playervaults.vaultmanagement.VaultOperations.VaultGate;

public class VaultManager {

    private static final String VAULTKEY = "vault%d";
    private static VaultManager instance;
    private final File directory = PlayerVaults.getInstance().getVaultData();
    private final Map<String, YamlConfiguration> cachedVaultFiles = new ConcurrentHashMap<>();
    private final PlayerVaults plugin;

    public VaultManager(PlayerVaults plugin) {
        this.plugin = plugin;
        instance = this;
    }

    /**
     * Get the instance of this class.
     *
     * @return - instance of this class.
     */
    public static VaultManager getInstance() {
        return instance;
    }

    /**
     * Resolve a stable, UUID-first key for a holder.
     */
    public static String normalizeHolderKey(String input) {
        if (input == null) {
            return null;
        }

        File dataDir = PlayerVaults.getInstance().getVaultData();
        File legacy = new File(dataDir, input + ".yml");
        if (legacy.exists()) {
            return input;
        }

        try {
            UUID uuid = UUID.fromString(input);
            return uuid.toString();
        } catch (Exception ignored) {
            // Ignored case/handler.
        }

        OfflinePlayer byName = Bukkit.getOfflinePlayer(input);
        if (byName != null && byName.getUniqueId() != null) {
            return byName.getUniqueId().toString();
        }

        return input;
    }

    /**
     * Saves the inventory to the specified player and vault number.
     *
     * @param inventory The inventory to be saved.
     * @param target The player of whose file to save to.
     * @param number The vault number.
     */
    public void saveVault(Inventory inventory, String target, int number) {
        final String holderKey = normalizeHolderKey(target);
        VaultGate.withLock(new VaultGate.VaultKey(holderKey, number), () -> {
            YamlConfiguration yaml = getPlayerVaultFile(holderKey, true);
            VaultOperations.getMaxVaultSize(holderKey);
            String serialized = CardboardBoxSerialization.toStorage(inventory, holderKey);
            yaml.set(String.format(VAULTKEY, number), serialized);
            saveFileSync(holderKey, yaml);
        });
    }

    /**
     * Load the player's vault and return it.
     *
     * @param player The holder of the vault.
     * @param number The vault number.
     */
    public Inventory loadOwnVault(Player player, int number, int size) {
        if (size % 9 != 0) {
            size = PlayerVaults.getInstance().getDefaultVaultSize();
        }

        PlayerVaults.debug("Loading self vault for " + player.getName() + " (" + player.getUniqueId() + ')');

        String title = PlayerVaults.getInstance().getVaultTitle(String.valueOf(number));
        VaultViewInfo info = new VaultViewInfo(player.getUniqueId().toString(), number);
        if (PlayerVaults.getInstance().getOpenInventories().containsKey(info.toString())) {
            PlayerVaults.debug("Already open");
            return PlayerVaults.getInstance().getOpenInventories().get(info.toString());
        }

        YamlConfiguration playerFile = getPlayerVaultFile(player.getUniqueId().toString(), true);
        VaultHolder vaultHolder = new VaultHolder(number);
        if (playerFile.getString(String.format(VAULTKEY, number)) == null) {
            PlayerVaults.debug("No vault matching number");
            Inventory inv = Bukkit.createInventory(vaultHolder, size, title);
            vaultHolder.setInventory(inv);
            return inv;
        } else {
            return getInventory(vaultHolder, player.getUniqueId().toString(), playerFile, size, number, title);
        }
    }

    /**
     * Load the player's vault and return it.
     *
     * @param name The holder of the vault.
     * @param number The vault number.
     */
    public Inventory loadOtherVault(String name, int number, int size) {
        if (size % 9 != 0) {
            size = PlayerVaults.getInstance().getDefaultVaultSize();
        }

        final String holderKey = normalizeHolderKey(name);
        PlayerVaults.debug("Loading other vault for " + holderKey);

        String title = PlayerVaults.getInstance().getVaultTitle(String.valueOf(number));
        VaultViewInfo info = new VaultViewInfo(holderKey, number);
        Inventory inv;
        VaultHolder vaultHolder = new VaultHolder(number);
        if (PlayerVaults.getInstance().getOpenInventories().containsKey(info.toString())) {
            PlayerVaults.debug("Already open");
            inv = PlayerVaults.getInstance().getOpenInventories().get(info.toString());
        } else {
            YamlConfiguration playerFile = getPlayerVaultFile(holderKey, true);
            Inventory i = getInventory(vaultHolder, holderKey, playerFile, size, number, title);
            if (i == null) {
                return null;
            } else {
                inv = i;
            }
            PlayerVaults.getInstance().getOpenInventories().put(info.toString(), inv);
        }
        return inv;
    }

    /**
     * Get an inventory from file. Returns null if the inventory doesn't exist. SHOULD ONLY BE USED INTERNALLY
     *
     * @param playerFile the YamlConfiguration file.
     * @param size the size of the vault.
     * @param number the vault number.
     * @return inventory if exists, otherwise null.
     */
    private Inventory getInventory(InventoryHolder owner, String ownerName, YamlConfiguration playerFile, int size, int number, String title) {
        Inventory inventory = Bukkit.createInventory(owner, size, title);

        String data = playerFile.getString(String.format(VAULTKEY, number));
        ItemStack[] deserialized = CardboardBoxSerialization.fromStorage(data, ownerName);
        if (deserialized == null) {
            PlayerVaults.debug("Loaded vault for " + ownerName + " as null");
            return inventory;
        }

        // Check if deserialized has more used slots than the limit here.
        // Happens on change of permission or if people used the broken version.
        // In this case, players will lose items.
        if (deserialized.length > size) {
            PlayerVaults.debug("Loaded vault for " + ownerName + " and got " + deserialized.length + " items for allowed size of " + size + ". Attempting to rescue!");
            for (ItemStack stack : deserialized) {
                if (stack != null) {
                    inventory.addItem(stack);
                }
            }
        } else {
            inventory.setContents(deserialized);
        }

        PlayerVaults.debug("Loaded vault");
        return inventory;
    }

    /**
     * Gets an inventory without storing references to it. Used for dropping a players inventories on death.
     *
     * @param holder The holder of the vault.
     * @param number The vault number.
     * @return The inventory of the specified holder and vault number. Can be null.
     */
    public Inventory getVault(String holder, int number) {
        String holderKey = normalizeHolderKey(holder);
        YamlConfiguration playerFile = getPlayerVaultFile(holderKey, true);
        String serialized = playerFile.getString(String.format(VAULTKEY, number));
        ItemStack[] contents = CardboardBoxSerialization.fromStorage(serialized, holderKey);
        int size = Math.max(9, ((contents.length + 8) / 9) * 9);
        Inventory inventory = Bukkit.createInventory(null, size, holderKey + " vault " + number);
        ItemStack[] copy = Arrays.copyOf(contents, size);
        inventory.setContents(copy);
        return inventory;
    }

    /**
     * Checks if a vault exists.
     *
     * @param holder holder of the vault.
     * @param number vault number.
     * @return true if the vault file and vault number exist in that file, otherwise false.
     */
    public boolean vaultExists(String holder, int number) {
        String holderKey = normalizeHolderKey(holder);
        File file = new File(directory, holderKey + ".yml");
        if (!file.exists()) {
            return false;
        }

        return getPlayerVaultFile(holderKey, true).contains(String.format(VAULTKEY, number));
    }

    /**
     * Gets the numbers belonging to all their vaults.
     *
     * @param holder holder
     * @return a set of Integers, which are player's vaults' numbers (fuck grammar).
     */
    public Set<Integer> getVaultNumbers(String holder) {
        Set<Integer> vaults = new HashSet<>();
        String holderKey = normalizeHolderKey(holder);
        YamlConfiguration file = getPlayerVaultFile(holderKey, true);
        if (file == null) {
            return vaults;
        }

        for (String s : file.getKeys(false)) {
            try {
                // vault%
                int number = Integer.parseInt(s.substring(4));
                vaults.add(number);
            } catch (NumberFormatException e) {
                // silent
            }
        }

        return vaults;
    }

    public void deleteAllVaults(String holder) {
        String holderKey = normalizeHolderKey(holder);
        removeCachedPlayerVaultFile(holderKey);
        deletePlayerVaultFile(holderKey);

        List<String> toRemove = new ArrayList<>();
        PlayerVaults.getInstance().getInVault().forEach((viewerId, info) -> {
            if (holderKey.equals(info.getVaultName())) {
                toRemove.add(viewerId);
                Player p = null;
                try {
                    p = Bukkit.getPlayer(UUID.fromString(viewerId));
                } catch (Exception ignored) {
                    // Ignored try actionable.
                }
                if (p != null) {
                    Player finalP = p;
                    PlayerVaults.scheduler().runAtEntity(finalP, task -> finalP.closeInventory());
                }
                PlayerVaults.getInstance().getOpenInventories().remove(info.toString());
            }
        });
        toRemove.forEach(id -> PlayerVaults.getInstance().getInVault().remove(id));
    }

    /**
     * Deletes a players vault.
     *
     * @param sender The sender of whom to send messages to.
     * @param holder The vault holder.
     * @param number The vault number.
     */
    public void deleteVault(CommandSender sender, final String holder, final int number) {
        final String holderKey = normalizeHolderKey(holder);
        final VaultGate.VaultKey gateKey = new VaultGate.VaultKey(holderKey, number);

        PlayerVaults.scheduler().runAsync(task ->
            VaultGate.withLock(gateKey, () -> {
                File file = new File(directory, holderKey + ".yml");
                if (!file.exists()) {
                    return;
                }

                YamlConfiguration playerFile = YamlConfiguration.loadConfiguration(file);
                playerFile.set(String.format(VAULTKEY, number), null);
                cachedVaultFiles.put(holderKey, playerFile);
                try {
                    playerFile.save(file);
                } catch (IOException ignored) {
                }

                String key = new VaultViewInfo(holderKey, number).toString();
                PlayerVaults.getInstance().getOpenInventories().remove(key);

                List<String> toRemove = new ArrayList<>();
                PlayerVaults.getInstance().getInVault().forEach((viewerId, info) -> {
                    if (holderKey.equals(info.getVaultName()) && info.getNumber() == number) {
                        toRemove.add(viewerId);
                        Player p = null;
                        try {
                            p = Bukkit.getPlayer(UUID.fromString(viewerId));
                        } catch (Exception ignored) {
                            // Ignored try actionable.
                        }
                        if (p != null) {
                            Player finalP = p;
                            PlayerVaults.scheduler().runAtEntity(finalP, t -> finalP.closeInventory());
                        }
                    }
                });
                toRemove.forEach(id -> PlayerVaults.getInstance().getInVault().remove(id));
            })
        );

        OfflinePlayer target = null;
        try {
            target = Bukkit.getOfflinePlayer(UUID.fromString(holderKey));
        } catch (Exception ignored) {
            // Ignored try actionable.
        }
        if (target != null && target.getName() != null) {
            if (sender.getName().equalsIgnoreCase(target.getName())) {
                this.plugin.getTL().deleteVault().title().with("vault", String.valueOf(number)).send(sender);
            } else {
                this.plugin.getTL().deleteOtherVault().title().with("vault", String.valueOf(number)).with("player", target.getName()).send(sender);
            }
        } else {
            if (sender.getName().equalsIgnoreCase(holder)) {
                this.plugin.getTL().deleteVault().title().with("vault", String.valueOf(number)).send(sender);
            } else {
                this.plugin.getTL().deleteOtherVault().title().with("vault", String.valueOf(number)).with("player", holder).send(sender);
            }
        }

        PlayerVaults.getInstance().getOpenInventories().remove(new VaultViewInfo(holderKey, number).toString());
    }

    // Should only be run asynchronously
    public void cachePlayerVaultFile(String holder) {
        String holderKey = normalizeHolderKey(holder);
        YamlConfiguration config = this.loadPlayerVaultFile(holderKey, false);
        if (config != null) {
            this.cachedVaultFiles.put(holderKey, config);
        }
    }

    public void removeCachedPlayerVaultFile(String holder) {
        String holderKey = normalizeHolderKey(holder);
        cachedVaultFiles.remove(holderKey);
    }

    /**
     * Get the holder's vault file. Create if doesn't exist.
     *
     * @param holder The vault holder.
     * @return The holder's vault config file.
     */
    public YamlConfiguration getPlayerVaultFile(String holder, boolean createIfNotFound) {
        String holderKey = resolveFileKey(holder);
        return cachedVaultFiles.computeIfAbsent(holderKey, key -> loadPlayerVaultFile(key, createIfNotFound));
    }

    public YamlConfiguration loadPlayerVaultFile(String holder) {
        return this.loadPlayerVaultFile(holder, true);
    }

    /**
     * Attempt to delete a vault file.
     *
     * @param holder UUID of the holder.
     */
    public void deletePlayerVaultFile(String holder) {
        String holderKey = resolveFileKey(holder);
        File file = new File(this.directory, holderKey + ".yml");
        if (file.exists()) {
            file.delete();
        }
    }

    public YamlConfiguration loadPlayerVaultFile(String uniqueId, boolean createIfNotFound) {
        if (!this.directory.exists()) {
            this.directory.mkdir();
        }

        File file = new File(this.directory, uniqueId + ".yml");
        if (!file.exists()) {
            if (createIfNotFound) {
                try {
                    file.createNewFile();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                return null;
            }
        }

        return YamlConfiguration.loadConfiguration(file);
    }

    public void saveFileSync(final String holder, final YamlConfiguration yaml) {
        String holderKey = resolveFileKey(holder);
        if (cachedVaultFiles.containsKey(holderKey)) {
            cachedVaultFiles.put(holderKey, yaml);
        }

        final boolean backups = PlayerVaults.getInstance().isBackupsEnabled();
        final File backupsFolder = PlayerVaults.getInstance().getBackupsFolder();
        final File file = new File(directory, holderKey + ".yml");
        if (file.exists() && backups) {
            file.renameTo(new File(backupsFolder, holderKey + ".yml"));
        }

        try {
            yaml.save(file);
        } catch (IOException e) {
            PlayerVaults.getInstance().addException(new IllegalStateException("Failed to save vault file for: " + holderKey, e));
            PlayerVaults.getInstance().getLogger().log(Level.SEVERE, "Failed to save vault file for: " + holderKey, e);
        }

        PlayerVaults.debug("Saved vault for " + holderKey);
    }

    private String resolveFileKey(String holder) {
        if (holder == null) {
            return null;
        }

        File legacy = new File(this.directory, holder + ".yml");
        if (legacy.exists()) {
            return holder;
        }

        return normalizeHolderKey(holder);
    }
}
