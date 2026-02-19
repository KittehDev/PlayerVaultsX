/*
 * PlayerVaultsX
 * Copyright (C) 2013 Trent Hensler, turt2live
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

package com.drtshock.playervaults.converters;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.vaultmanagement.VaultManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class XVaultsConverter implements Converter {

    @SuppressWarnings("unchecked")
    @Override
    public int run(CommandSender initiator) {
        PlayerVaults plugin = PlayerVaults.getInstance();
        VaultManager vaultManager = VaultManager.getInstance();

        Path xvaultsFolder = PlayerVaults.getInstance().getDataFolder().getParentFile().toPath().resolve("XVaults");

        Set<String> uuids = new HashSet<>();

        for (int v = 1; v <= 10; v++) {
            Path file = xvaultsFolder.resolve("vault_" + v + "_data.yml");
            if (Files.exists(file)) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file.toFile());
                int curVault = v;
                yaml.getKeys(false).forEach(k -> {
                    try {
                        ItemStack[] items = ((List<?>) yaml.get(k)).toArray(new ItemStack[0]);
                        Inventory inventory = Bukkit.createInventory(null, items.length % 9 == 0 ? items.length : (6 * 9), "Converting!");
                        inventory.setContents(items);
                        vaultManager.saveVault(inventory, k, curVault);
                        uuids.add(k);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        }

        return uuids.size();
    }

    @Override
    public boolean canConvert() {
        return new File(PlayerVaults.getInstance().getDataFolder().getParentFile(), "XVaults").exists();
    }

    @Override
    public String getName() {
        return "XVaults";
    }
}
