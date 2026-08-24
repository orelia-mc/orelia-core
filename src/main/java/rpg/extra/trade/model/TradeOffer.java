package rpg.extra.trade.model;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** One side of a {@link TradeSession}: the items offered and whether that side has confirmed. */
public final class TradeOffer {

    private final List<ItemStack> items = new ArrayList<>();
    private double money;
    private boolean confirmed;

    public List<ItemStack> getItems() {
        return items;
    }

    public void addItem(ItemStack item) {
        items.add(item);
        confirmed = false;
    }

    public ItemStack removeItem(int index) {
        if (index < 0 || index >= items.size()) {
            return null;
        }
        confirmed = false;
        return items.remove(index);
    }

    public double getMoney() {
        return money;
    }

    /** Changing the offered amount un-confirms this side, same as adding/removing an item. */
    public void setMoney(double money) {
        this.money = money;
        confirmed = false;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(boolean confirmed) {
        this.confirmed = confirmed;
    }
}
