package com.drtshock.playervaults.placeholder;

import com.drtshock.playervaults.vaultmanagement.VaultOperations;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class Papi extends PlaceholderExpansion {
    private final String version;

    public Papi(String version) {
        this.version = version;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "playervaults";
    }

    @Override
    public @NotNull String getAuthor() {
        return "mbaxter";
    }

    @Override
    public @NotNull String getVersion() {
        return this.version;
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        if (player == null) {
            return null;
        }
        if (params.equals("vaults_max_by_perms")) {
            return String.valueOf(VaultOperations.countVaults(player));
        }
        return null;
    }
}
