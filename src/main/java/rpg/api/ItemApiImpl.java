package rpg.api;

import org.bukkit.inventory.ItemStack;
import rpg.item.manager.ItemManager;
import rpg.status.model.PlayerStatusComponent;
import rpg.status.service.StatusService;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class ItemApiImpl implements ItemApi {

    private final ItemManager itemManager;
    private final StatusService statusService;

    ItemApiImpl(ItemManager itemManager, StatusService statusService) {
        this.itemManager = itemManager;
        this.statusService = statusService;
    }

    @Override
    public Optional<ItemStack> createWeapon(String weaponId) {
        return itemManager.createWeapon(weaponId);
    }

    @Override
    public Optional<String> identifyWeapon(ItemStack stack) {
        return itemManager.getIdentityService().idOf(stack);
    }

    @Override
    public Set<String> getAllWeaponIds() {
        return itemManager.getAllWeapons().keySet();
    }

    @Override
    public boolean weaponMeetsRequirements(UUID playerId, String weaponId) {
        return itemManager.findById(weaponId)
                .map(data -> itemManager.getRequirementService().meetsRequirements(playerId, data))
                .orElse(false);
    }

    @Override
    public int getEnhancementLevel(ItemStack stack) {
        return itemManager.getIdentityService().getEnhancementLevel(stack);
    }

    @Override
    public int enhanceWeapon(ItemStack stack) {
        return itemManager.getIdentityService().enhance(stack);
    }

    @Override
    public int getWeaponLevel(ItemStack stack) {
        return itemManager.getIdentityService().dataOf(stack)
                .map(data -> itemManager.getIdentityService().getWeaponLevel(stack, data))
                .orElse(0);
    }

    @Override
    public int levelUpWeapon(UUID playerId, ItemStack stack) {
        var data = itemManager.getIdentityService().dataOf(stack).orElse(null);
        if (data == null) {
            return -1;
        }
        int playerLevel = statusService.component(playerId).map(PlayerStatusComponent::getLevel).orElse(1);
        return itemManager.getIdentityService().levelUp(stack, data, playerLevel);
    }

    @Override
    public int getWeaponLevelCap(UUID playerId) {
        int playerLevel = statusService.component(playerId).map(PlayerStatusComponent::getLevel).orElse(1);
        return itemManager.getIdentityService().weaponLevelCap(playerLevel);
    }

    @Override
    public void refreshWeaponLore(ItemStack stack) {
        itemManager.getIdentityService().dataOf(stack)
                .ifPresent(data -> itemManager.refreshWeaponLore(stack, data));
    }
}
