package rpg.item.manager;

import rpg.item.model.WeaponData;
import rpg.item.repository.WeaponRepository;
import rpg.item.service.WeaponFactory;
import rpg.item.service.WeaponIdentityService;
import rpg.item.service.WeaponRequirementService;

import java.util.Map;
import java.util.Optional;
import org.bukkit.inventory.ItemStack;

/**
 * Facade the rest of the plugin uses to work with weapons, so callers never need to know
 * about {@link WeaponRepository}/{@link WeaponFactory}/{@link WeaponIdentityService} individually.
 */
public final class ItemManager {

    private final WeaponRepository repository;
    private final WeaponFactory factory;
    private final WeaponIdentityService identityService;
    private final WeaponRequirementService requirementService;

    public ItemManager(WeaponRepository repository, WeaponFactory factory,
                        WeaponIdentityService identityService, WeaponRequirementService requirementService) {
        this.repository = repository;
        this.factory = factory;
        this.identityService = identityService;
        this.requirementService = requirementService;
    }

    public Optional<ItemStack> createWeapon(String id) {
        return repository.findById(id).map(factory::create);
    }

    public Optional<WeaponData> findById(String id) {
        return repository.findById(id);
    }

    public Map<String, WeaponData> getAllWeapons() {
        return repository.getAll();
    }

    /** Re-renders {@code stack}'s lore against its current weapon level/enhancement - call after {@link WeaponIdentityService#levelUp}/{@code #enhance}. */
    public void refreshWeaponLore(ItemStack stack, WeaponData data) {
        factory.refreshLore(stack, data, identityService);
    }

    public WeaponIdentityService getIdentityService() {
        return identityService;
    }

    public WeaponRequirementService getRequirementService() {
        return requirementService;
    }
}
