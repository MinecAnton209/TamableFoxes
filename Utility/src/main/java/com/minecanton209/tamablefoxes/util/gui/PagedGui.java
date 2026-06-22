package com.minecanton209.tamablefoxes.util.gui;

import com.cryptomorin.xseries.XMaterial;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class PagedGui implements InventoryHolder {

    private final String title;
    private final int rows;
    private final List<ItemStack> items;
    private Inventory currentInventory;
    private int currentPage = 0;
    private int previousPageSlot = -1;
    private int nextPageSlot = -1;
    private XMaterial previousPageMaterial = XMaterial.ARROW;
    private XMaterial nextPageMaterial = XMaterial.ARROW;
    private String previousPageName = "&cPrevious Page";
    private String nextPageName = "&aNext Page";
    private Gui.ClickHandler itemClickHandler;
    private boolean playSound = false;

    private PagedGui(String title, int rows, List<ItemStack> items) {
        this.title = ChatColor.translateAlternateColorCodes('&', title);
        this.rows = rows;
        this.items = items;
    }

    public static PagedGui create(String title, int rows, List<ItemStack> items) {
        return new PagedGui(title, rows, new ArrayList<>(items));
    }

    public PagedGui previousPage(int slot, XMaterial material) {
        this.previousPageSlot = slot;
        this.previousPageMaterial = material;
        return this;
    }

    public PagedGui previousPage(int slot, XMaterial material, String name) {
        this.previousPageSlot = slot;
        this.previousPageMaterial = material;
        this.previousPageName = name;
        return this;
    }

    public PagedGui nextPage(int slot, XMaterial material) {
        this.nextPageSlot = slot;
        this.nextPageMaterial = material;
        return this;
    }

    public PagedGui nextPage(int slot, XMaterial material, String name) {
        this.nextPageSlot = slot;
        this.nextPageMaterial = material;
        this.nextPageName = name;
        return this;
    }

    public PagedGui onItemClick(Gui.ClickHandler handler) {
        this.itemClickHandler = handler;
        return this;
    }

    public PagedGui sound(boolean playSound) {
        this.playSound = playSound;
        return this;
    }

    public void open(Player player) {
        openPage(player, 0);
    }

    public void openPage(Player player, int page) {
        this.currentPage = page;
        int slots = rows * 9;
        Inventory inventory = org.bukkit.Bukkit.createInventory(this, slots, title);
        this.currentInventory = inventory;

        int contentSlots = slots;
        if (previousPageSlot >= 0) contentSlots--;
        if (nextPageSlot >= 0) contentSlots--;

        int startIndex = page * contentSlots;
        int slotIndex = 0;

        for (int i = 0; i < slots; i++) {
            if (i == previousPageSlot || i == nextPageSlot) continue;
            if (startIndex + slotIndex < items.size()) {
                inventory.setItem(i, items.get(startIndex + slotIndex));
                slotIndex++;
            }
        }

        if (previousPageSlot >= 0 && page > 0) {
            inventory.setItem(previousPageSlot, new ItemBuilder(previousPageMaterial)
                .name(previousPageName).build());
        }
        if (nextPageSlot >= 0 && (page + 1) * contentSlots < items.size()) {
            inventory.setItem(nextPageSlot, new ItemBuilder(nextPageMaterial)
                .name(nextPageName).build());
        }

        player.openInventory(inventory);
        if (playSound) {
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }

    public void handleClick(InventoryClickEvent event) {
        int slot = event.getRawSlot();
        Player player = (Player) event.getWhoClicked();
        int slots = rows * 9;

        if (slot == previousPageSlot && currentPage > 0) {
            openPage(player, currentPage - 1);
            return;
        }
        if (slot == nextPageSlot) {
            int contentSlots = slots;
            if (previousPageSlot >= 0) contentSlots--;
            if (nextPageSlot >= 0) contentSlots--;
            if ((currentPage + 1) * contentSlots < items.size()) {
                openPage(player, currentPage + 1);
            }
            return;
        }

        if (itemClickHandler != null) {
            itemClickHandler.onClick(event, null);
        }
    }

    public int getCurrentPage() { return currentPage; }
    public int getTotalPages() {
        int contentSlots = rows * 9;
        if (previousPageSlot >= 0) contentSlots--;
        if (nextPageSlot >= 0) contentSlots--;
        return (int) Math.ceil((double) items.size() / contentSlots);
    }

    @Override
    public Inventory getInventory() {
        return currentInventory;
    }
}
