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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

public class FairyVaultsConverter implements Converter {

    @Override
    public int run(CommandSender initiator) {
        PlayerVaults plugin = PlayerVaults.getInstance();
        VaultManager vaultManager = VaultManager.getInstance();


        Plugin fairyPlugin = plugin.getServer().getPluginManager().getPlugin("FairyVaults");
        if (fairyPlugin == null) {
            plugin.getLogger().warning("FairyVaults not running. Need it to convert.");
            return -1;
        }

        Map<String, AtomicInteger> vaultIds = new HashMap<>();
        int count = 0;

        try {
            Object databaseManager = fairyPlugin.getClass().getMethod("databaseManager").invoke(fairyPlugin);

            Field connSourceField = databaseManager.getClass().getDeclaredField("connectionSource");
            connSourceField.setAccessible(true);
            Object connectionSource = connSourceField.get(databaseManager);

            Object readOnlyConnection = connectionSource.getClass().getMethod("getReadOnlyConnection", String.class).invoke(connectionSource, "vaults");

            Connection connection = (Connection) readOnlyConnection.getClass().getMethod("getUnderlyingConnection").invoke(readOnlyConnection);

            ResultSet results = connection.prepareStatement("select vaults.id as vaultid, vault_users.player_id as playerid,vaults.items as itemset from vaults, vault_users where vault_users.id = vaults.user_id order by vaults.id asc;").executeQuery();

            Class<?> virtInvClass = Class.forName("de.rexlmanu.fairyvaults.libs.invui.invui.inventory.VirtualInventory");
            Method deserialize = virtInvClass.getMethod("deserialize", byte[].class);
            deserialize.setAccessible(true);
            Method getItems = virtInvClass.getMethod("getUnsafeItems");
            getItems.setAccessible(true);

            while (results.next()) {
                try {
                    String uuid = results.getString("playerid");
                    byte[] items = results.getBytes("itemset");
                    Object virtInv = deserialize.invoke(null, (Object) items);
                    ItemStack[] itemsStacks = (ItemStack[]) getItems.invoke(virtInv);
                    Inventory inventory = Bukkit.createInventory(null, items.length % 9 == 0 && items.length < 55 ? items.length : (6 * 9), "Converting!");
                    inventory.setContents(itemsStacks);
                    vaultManager.saveVault(inventory, uuid, vaultIds.computeIfAbsent(uuid, k -> new AtomicInteger()).incrementAndGet());
                    count++;
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Invalid data found for vault id: " + results.getInt("vaultid"), e);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to set up conversion", e);
            return -1;
        }

        return count;

    }

    @Override
    public boolean canConvert() {
        return Bukkit.getServer().getPluginManager().isPluginEnabled("FairyVaults");
    }

    @Override
    public String getName() {
        return "FairyVaults";
    }
}
