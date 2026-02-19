package com.drtshock.playervaults.vaultmanagement;

import com.drtshock.playervaults.PlayerVaults;
import dev.kitteh.cardboardbox.CardboardBox;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class CardboardBoxSerialization {
    private record BadData(String message, String data) {
    }

    public static String toStorage(Inventory inventory, String target) {
        try {
            return Base64Coder.encodeLines(writeInventory(inventory.getContents()));
        } catch (Exception e) {
            throw PlayerVaults.getInstance().addException(new IllegalStateException("Failed to save items for " + target, e));
        }
    }

    public static ItemStack[] fromStorage(String data, String target) {
        if (data == null || data.isEmpty()) {
            ItemStack[] i = new ItemStack[6 * 9];
            for (int x = 0; x < i.length; x++) {
                i[x] = new ItemStack(Material.AIR);
            }
            return i;
        }

        try {
            DataInputStream input = new DataInputStream(new ByteArrayInputStream(Base64Coder.decodeLines(data)));
            ItemStack[] contents = new ItemStack[input.readInt()];
            List<BadData> exceptional = new ArrayList<>();
            for (int i = 0; i < contents.length; i++) {
                int len = input.readInt();
                byte[] itemBytes = new byte[len];
                input.readFully(itemBytes);
                try {
                    contents[i] = CardboardBox.deserializeItem(itemBytes);
                } catch (Exception e) {
                    if (e.getMessage().startsWith("Cardboard Box")) {
                        throw e;
                    }
                    exceptional.add(new BadData(e.getMessage(), Base64Coder.encodeLines(itemBytes)));
                    contents[i] = new ItemStack(Material.AIR);
                }
            }
            if (!exceptional.isEmpty()) {
                String output = exceptional.stream().map(e -> e.message + "\n" + e.data).collect(Collectors.joining("\n"));
                PlayerVaults.getInstance().addException(new IllegalStateException("Failed to load items for " + target + "\n" + output));
                PlayerVaults.getInstance().getLogger().log(Level.SEVERE, "Failed to load items for " + target);
                PlayerVaults.getInstance().getLogger().log(Level.SEVERE, "Items:\n" + output);
            }
            return contents;
        } catch (Exception e) {
            PlayerVaults.getInstance().addException(new IllegalStateException("Failed to load items for " + target + "\n" + data, e));
            PlayerVaults.getInstance().getLogger().log(Level.SEVERE, "Failed to load items for " + target, e);
            PlayerVaults.getInstance().getLogger().log(Level.SEVERE, "Data: " + data);
            return null;
        }
    }

    public static byte[] writeInventory(ItemStack[] contents) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(contents.length);
        for (ItemStack content : contents) {
            byte[] item = CardboardBox.serializeItem(content);
            out.writeInt(item.length);
            out.write(item);
        }
        out.close();
        return bytes.toByteArray();
    }
}
