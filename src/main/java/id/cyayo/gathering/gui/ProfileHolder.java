package id.cyayo.gathering.gui;

import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public class ProfileHolder implements InventoryHolder {

    public enum ViewType {
        MAIN_PROFILE,
        CATEGORY_DETAIL
    }

    private final ViewType viewType;
    private final OfflinePlayer targetPlayer;
    private final String category;
    private final int page;
    private Inventory inventory;

    public ProfileHolder(ViewType viewType, OfflinePlayer targetPlayer, String category, int page) {
        this.viewType = viewType;
        this.targetPlayer = targetPlayer;
        this.category = category;
        this.page = page;
    }

    public ProfileHolder(ViewType viewType, OfflinePlayer targetPlayer) {
        this(viewType, targetPlayer, null, 1);
    }

    public ViewType getViewType() {
        return viewType;
    }

    public OfflinePlayer getTargetPlayer() {
        return targetPlayer;
    }

    public String getCategory() {
        return category;
    }

    public int getPage() {
        return page;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}

