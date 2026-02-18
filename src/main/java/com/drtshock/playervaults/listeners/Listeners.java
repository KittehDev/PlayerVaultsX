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

package com.drtshock.playervaults.listeners;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.config.file.Translation;
import com.drtshock.playervaults.events.BlacklistedItemEvent;
import com.drtshock.playervaults.util.Permission;
import com.drtshock.playervaults.vaultmanagement.VaultHolder;
import com.drtshock.playervaults.vaultmanagement.VaultManager;
import com.drtshock.playervaults.vaultmanagement.VaultViewInfo;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class Listeners implements Listener {

    public final PlayerVaults plugin;
    private final VaultManager vaultManager = VaultManager.getInstance();

    public Listeners(PlayerVaults playerVaults) {
        this.plugin = playerVaults;
    }

    public void saveVault(Player player, Inventory inventory) {
        VaultViewInfo info = plugin.getInVault().remove(player.getUniqueId().toString());
        if (info != null) {
            boolean badDay = false;
            if (!(inventory.getHolder() instanceof VaultHolder)) {
                PlayerVaults.getInstance().getLogger().severe("Encountered lost vault situation for player '" + player.getName() + "', instead finding a '" + inventory.getType() + "' - attempting to save the vault if no viewers present");
                badDay = true;
                inventory = plugin.getOpenInventories().get(info.toString());
                if (inventory == null) {
                    PlayerVaults.getInstance().getLogger().severe("Could not find inventory");
                    return;
                }
            }
            Inventory inv = Bukkit.createInventory(null, inventory.getSize());
            inv.setContents(inventory.getContents().clone());

            PlayerVaults.debug(inventory.getType() + " " + inventory.getClass().getSimpleName());
            if (inventory.getViewers().size() <= 1) {
                PlayerVaults.debug("Saving!");
                vaultManager.saveVault(inv, info.getVaultName(), info.getNumber());
                plugin.getOpenInventories().remove(info.toString());
            } else {
                if (badDay) {
                    PlayerVaults.getInstance().getLogger().severe("Viewers size >0: " + inventory.getViewers().stream().map(HumanEntity::getName).collect(Collectors.joining(", ")));
                }
                PlayerVaults.debug("Other viewers found, not saving! " + inventory.getViewers().stream().map(HumanEntity::getName).collect(Collectors.joining(" ")));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN) {
            return;
        }
        Player p = event.getPlayer();
        // The player will either quit, die, or close the inventory at some point
        if (plugin.getInVault().containsKey(p.getUniqueId().toString())) {
            return;
        }
        saveVault(p, p.getOpenInventory().getTopInventory());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        saveVault(event.getPlayer(), event.getPlayer().getOpenInventory().getTopInventory());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        saveVault(event.getEntity(), event.getEntity().getOpenInventory().getTopInventory());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClose(InventoryCloseEvent event) {
        saveVault((Player) event.getPlayer(), event.getInventory());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        EntityType type = event.getRightClicked().getType();
        if ((type == EntityType.VILLAGER || type == EntityType.MINECART) && PlayerVaults.getInstance().getInVault().containsKey(player.getUniqueId().toString())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory != null) {
            VaultViewInfo info = PlayerVaults.getInstance().getInVault().get(player.getUniqueId().toString());
            if (info != null) {
                int num = info.getNumber();
                String inventoryTitle = event.getView().getTitle();
                String title = this.plugin.getVaultTitle(String.valueOf(num));
                if (inventoryTitle.equalsIgnoreCase(title)) {
                    ItemStack[] items = new ItemStack[2];
                    items[0] = event.getCurrentItem();
                    if (event.getHotbarButton() > -1 && event.getWhoClicked().getInventory().getItem(event.getHotbarButton()) != null) {
                        items[1] = event.getWhoClicked().getInventory().getItem(event.getHotbarButton());
                    }
                    if (event.getClick().name().equals("SWAP_OFFHAND")) {
                        items[1] = event.getWhoClicked().getInventory().getItemInOffHand();
                    }

                    if (!player.hasPermission(Permission.BYPASS_BLOCKED_ITEMS)) {
                        for (ItemStack item : items) {
                            if (item == null) {
                                continue;
                            }
                            if (this.isBlocked(player, item, info)) {
                                event.setCancelled(true);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();

        Inventory clickedInventory = event.getInventory();
        if (clickedInventory != null) {
            VaultViewInfo info = PlayerVaults.getInstance().getInVault().get(player.getUniqueId().toString());
            if (info != null) {
                int num = info.getNumber();
                String inventoryTitle = event.getView().getTitle();
                String title = this.plugin.getVaultTitle(String.valueOf(num));
                if ((inventoryTitle != null && inventoryTitle.equalsIgnoreCase(title)) && event.getNewItems() != null) {
                    if (!player.hasPermission(Permission.BYPASS_BLOCKED_ITEMS)) {
                        for (ItemStack item : event.getNewItems().values()) {
                            if (this.isBlocked(player, item, info)) {
                                event.setCancelled(true);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    private boolean isBlocked(Player player, ItemStack item, VaultViewInfo info) {
        List<BlacklistedItemEvent.Reason> reasons = new ArrayList<>();
        Map<BlacklistedItemEvent.Reason, Translation.TL.Builder> responses = new HashMap<>();
        if (PlayerVaults.getInstance().isBlockWithModelData() && ((item.getItemMeta() instanceof ItemMeta i) && i.hasCustomModelData())) {
            reasons.add(BlacklistedItemEvent.Reason.HAS_MODEL_DATA);
            responses.put(BlacklistedItemEvent.Reason.HAS_MODEL_DATA, this.plugin.getTL().blockedItemWithModelData().title());
        }
        if (PlayerVaults.getInstance().isBlockWithoutModelData() && !((item.getItemMeta() instanceof ItemMeta i) && i.hasCustomModelData())) {
            reasons.add(BlacklistedItemEvent.Reason.HAS_NO_MODEL_DATA);
            responses.put(BlacklistedItemEvent.Reason.HAS_NO_MODEL_DATA, this.plugin.getTL().blockedItemWithoutModelData().title());
        }
        if (PlayerVaults.getInstance().isBlockedMaterial(item.getType())) {
            reasons.add(BlacklistedItemEvent.Reason.TYPE);
            responses.put(BlacklistedItemEvent.Reason.TYPE, this.plugin.getTL().blockedItem().title().with("item", item.getType().name()));
        }
        Set<Enchantment> ench = PlayerVaults.getInstance().isEnchantmentBlocked(item);
        if (!ench.isEmpty()) {
            reasons.add(BlacklistedItemEvent.Reason.ENCHANTMENT);
            responses.put(BlacklistedItemEvent.Reason.ENCHANTMENT, this.plugin.getTL().blockedItemWithEnchantments().title());
        }
        if (!reasons.isEmpty()) {
            BlacklistedItemEvent event = new BlacklistedItemEvent(player, item, reasons, info.getVaultName(), info.getNumber());
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
                responses.get(event.getReasons().getFirst()).send(player);
                return true;
            }
        }
        return false;
    }
}
