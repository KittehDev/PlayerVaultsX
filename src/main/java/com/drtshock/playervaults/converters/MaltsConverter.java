package com.drtshock.playervaults.converters;

import com.drtshock.playervaults.PlayerVaults;
import com.drtshock.playervaults.vaultmanagement.VaultManager;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.Inventory;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class MaltsConverter implements Converter {

    private static final String MALTS_API = "dev.jsinco.malts.api.MaltsAPI";


    @Override
    public int run(CommandSender initiator) {
        PlayerVaults plugin = PlayerVaults.getInstance();
        VaultManager vaultManager = VaultManager.getInstance();
        int convertedCount = 0;

        try {
            CompletableFuture<Collection<Object>> allVaults = (CompletableFuture<Collection<Object>>) Class.forName(MALTS_API).getDeclaredMethod("getAllVaults").invoke(null);
            Collection<Object> vaults = allVaults.join(); // Block thread I guess


            for (Object vault : vaults) {
                try {
                    UUID owner = (UUID) vault.getClass().getMethod("getOwner").invoke(vault);
                    int id = (int) vault.getClass().getMethod("getId").invoke(vault);
                    Inventory inventory = (Inventory) vault.getClass().getMethod("getInventory").invoke(vault);

                    vaultManager.saveVault(inventory, owner.toString(), id);
                    convertedCount++;
                } catch (ReflectiveOperationException e) {
                    plugin.getLogger().severe("Failed to convert a vault: " + e.getMessage());
                }
            }
            initiator.sendMessage("Converted " + convertedCount + " vaults from Malts.");

        } catch (ReflectiveOperationException e) {
            initiator.getServer().getLogger().log(Level.SEVERE, "Failed to convert vaults", e);
            return -1;
        }
        return convertedCount;
    }

    @Override
    public boolean canConvert() {
        return Bukkit.getServer().getPluginManager().isPluginEnabled("Malts");
    }

    @Override
    public String getName() {
        return "Malts";
    }
}
