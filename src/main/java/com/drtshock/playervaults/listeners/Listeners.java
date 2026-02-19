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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Listeners implements Listener {

    public final PlayerVaults plugin;
    private final VaultManager vaultManager = VaultManager.getInstance();

    //  Guards against double-saves per player
    private final Set<UUID> savePending = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public Listeners(PlayerVaults playerVaults) {
        this.plugin = playerVaults;
    }

    public void saveVault(Player player, Inventory inventory) {
        UUID uuid = player.getUniqueId();

        if (!savePending.add(uuid)) {
            PlayerVaults.debug("Save already pending for " + player.getName() + ", skipping duplicate.");
            return;
        }

        try {
            VaultViewInfo info = plugin.getInVault().remove(uuid.toString());
            if (info == null) return;

            // This prevents ghost-item exploits
            final ItemStack[] snapshot = snapshotInventory(inventory, info, player);
            if (snapshot == null) {
                // Re-insert info so a future close/quit can retry
                plugin.getInVault().put(uuid.toString(), info);
                return;
            }

            plugin.getOpenInventories().remove(info.toString());

            // Build a clean inventory from the validated snapshot and hand it to the manager
            Inventory saveInv = Bukkit.createInventory(null, inventory.getSize());
            saveInv.setContents(snapshot);

            vaultManager.saveVault(saveInv, info.getVaultName(), info.getNumber());
        } finally {
            // Always release the guard so future opens of this vault work correctly
            savePending.remove(uuid);
        }
    }

    private ItemStack[] snapshotInventory(Inventory inventory, VaultViewInfo info, Player player) {
        if (inventory.getViewers().size() > 1) {
            Inventory tracked = plugin.getOpenInventories().get(info.toString());
            if (tracked == null) {
                PlayerVaults.getInstance().getLogger().severe(
                        "Could not resolve shared inventory for " + player.getName());
                return null;
            }
            // Don't save, the last viewer to close will handle it
            PlayerVaults.debug("Other viewers present for " + player.getName() + ", deferring save.");
            return null;
        }

        if (!(inventory.getHolder() instanceof VaultHolder) && inventory.getViewers().size() > 0) {
            PlayerVaults.getInstance().getLogger().warning(
                    "Unexpected holder type for " + player.getName() + ": " + inventory.getType() +
                            " — contents will still be saved.");
        }

        ItemStack[] raw = inventory.getContents();
        ItemStack[] sanitised = new ItemStack[raw.length];

        for (int i = 0; i < raw.length; i++) {
            ItemStack item = raw[i];
            if (item == null) {
                sanitised[i] = null;
                continue;
            }
            int amount = item.getAmount();
            int maxStack = item.getMaxStackSize();
            if (amount < 1 || amount > maxStack) {
                ItemStack clamped = item.clone();
                clamped.setAmount(Math.max(1, Math.min(amount, maxStack)));
                sanitised[i] = clamped;
            } else sanitised[i] = item.clone();
        }
        return sanitised;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN) {
            return;
        }
        Player p = event.getPlayer();
        // The player will either quit, die, or close the inventory at some point
        if (plugin.getInVault().containsKey(p.getUniqueId().toString()))
            p.closeInventory();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (plugin.getInVault().containsKey(player.getUniqueId().toString())) {
            saveVault(player, player.getOpenInventory().getTopInventory());
        }
        savePending.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (plugin.getInVault().containsKey(player.getUniqueId().toString())) {
            saveVault(player, player.getOpenInventory().getTopInventory());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        VaultViewInfo info = plugin.getInVault().get(player.getUniqueId().toString());

        // If the player is still inside the inventory
        if (info != null) {

            // Put the items to a new inventory, so the system can save and the garbage collector can move faster
            Inventory inv = Bukkit.createInventory(null, event.getInventory().getSize());
            inv.setContents(event.getInventory().getContents().clone());

            saveVault(player, inv);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        EntityType type = event.getRightClicked().getType();
        if ((type == EntityType.VILLAGER || type == EntityType.MINECART)
                && plugin.getInVault().containsKey(player.getUniqueId().toString())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        Inventory clickedInventory = event.getClickedInventory();
        if (clickedInventory == null) return;

        VaultViewInfo info = plugin.getInVault().get(player.getUniqueId().toString());
        if (info == null) return;

        String inventoryTitle = event.getView().getTitle();
        String title = this.plugin.getVaultTitle(String.valueOf(info.getNumber()));
        if (!inventoryTitle.equalsIgnoreCase(title))
            return;

        // This prevents duplication via SWAP_OFFHAND or hotbar-number key
        ItemStack[] items = new ItemStack[2];
        items[0] = event.getCurrentItem();

        String clickName = event.getClick().name();
        if (event.getHotbarButton() > -1) {
            items[1] = player.getInventory().getItem(event.getHotbarButton());
        } else if (clickName.equals("SWAP_OFFHAND")) {
            items[1] = player.getInventory().getItemInOffHand();
        }

        // Cancel all clicks to prevent ghost items being inserted after snapshot
        if (savePending.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (!player.hasPermission(Permission.BYPASS_BLOCKED_ITEMS)) {
            for (ItemStack item : items) {
                if (item == null) continue;
                if (this.isBlocked(player, item, info)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player))
            return;

        Inventory draggedInventory = event.getInventory();
        if (draggedInventory == null) return;

        VaultViewInfo info = plugin.getInVault().get(player.getUniqueId().toString());
        if (info == null) return;

        String inventoryTitle = event.getView().getTitle();
        String title = this.plugin.getVaultTitle(String.valueOf(info.getNumber()));
        if (inventoryTitle == null || !inventoryTitle.equalsIgnoreCase(title))
            return;

        // Block drags while a save is pending to prevent ghost-item insertion.
        if (savePending.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (!player.hasPermission(Permission.BYPASS_BLOCKED_ITEMS) && event.getNewItems() != null) {
            for (ItemStack item : event.getNewItems().values()) {
                if (this.isBlocked(player, item, info)) {
                    event.setCancelled(true);
                    return;
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