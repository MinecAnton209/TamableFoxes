package com.minecanton209.tamablefoxes.util.gui;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class Gui implements InventoryHolder {

    private final Inventory inventory;
    private final Map<Integer, ClickHandler> clickHandlers = new HashMap<>();
    private boolean playSound = false;

    public Gui(String title, int rows) {
        this.inventory = Bukkit.createInventory(this, rows * 9, ChatColor.translateAlternateColorCodes('&', title));
    }

    public Gui item(int slot, ItemStack item) {
        inventory.setItem(slot, item);
        return this;
    }

    public Gui onClick(int slot, ClickHandler handler) {
        clickHandlers.put(slot, handler);
        return this;
    }

    public Gui fill(ItemStack filler) {
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, filler);
            }
        }
        return this;
    }

    public Gui sound(boolean playSound) {
        this.playSound = playSound;
        return this;
    }

    public void open(Player player) {
        player.openInventory(inventory);
        if (playSound) {
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }

    public void close() {
        for (var viewer : inventory.getViewers()) {
            viewer.closeInventory();
        }
    }

    public void handleClick(InventoryClickEvent event) {
        ClickHandler handler = clickHandlers.get(event.getRawSlot());
        if (handler != null) {
            handler.onClick(event, this);
        }
    }

    public boolean hasClickHandler(int slot) {
        return clickHandlers.containsKey(slot);
    }

    public boolean shouldPlaySound() {
        return playSound;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    @FunctionalInterface
    public interface ClickHandler {
        void onClick(InventoryClickEvent event, Gui gui);
    }
}
