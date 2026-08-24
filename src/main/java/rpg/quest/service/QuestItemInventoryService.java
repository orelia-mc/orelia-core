package rpg.quest.service;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import rpg.api.ItemApi;
import rpg.quest.model.QuestObjective;

/**
 * Matches {@link rpg.quest.model.ObjectiveType#COLLECT_ITEM} / {@code DELIVER_ITEM}
 * objectives against a player's inventory. {@code target-id} on the objective is either
 * a weapon id (resolved through orelia-core's {@link ItemApi}) or a vanilla material name.
 */
public final class QuestItemInventoryService {

    private final ItemApi itemApi;

    public QuestItemInventoryService(ItemApi itemApi) {
        this.itemApi = itemApi;
    }

    public int countMatching(Player player, QuestObjective objective) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getStorageContents()) {
            if (matches(stack, objective.getTargetId())) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Removes up to {@code amount} matching items. Returns false (no change) if not enough are present. */
    public boolean consume(Player player, QuestObjective objective, int amount) {
        if (countMatching(player, objective) < amount) {
            return false;
        }
        PlayerInventory inventory = player.getInventory();
        int remaining = amount;
        ItemStack[] contents = inventory.getStorageContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack stack = contents[i];
            if (!matches(stack, objective.getTargetId())) {
                continue;
            }
            int take = Math.min(remaining, stack.getAmount());
            stack.setAmount(stack.getAmount() - take);
            contents[i] = stack.getAmount() <= 0 ? null : stack;
            remaining -= take;
        }
        inventory.setStorageContents(contents);
        return true;
    }

    private boolean matches(ItemStack stack, String targetId) {
        if (stack == null || targetId == null) {
            return false;
        }
        boolean isWeaponMatch = itemApi.identifyWeapon(stack).map(targetId::equals).orElse(false);
        if (isWeaponMatch) {
            return true;
        }
        try {
            return stack.getType() == Material.valueOf(targetId.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
