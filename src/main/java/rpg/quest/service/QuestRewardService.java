package rpg.quest.service;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import rpg.api.AccessoryApi;
import rpg.api.ItemApi;
import rpg.api.SkillApi;
import rpg.api.StatusApi;
import rpg.core.player.PlayerDataManager;
import rpg.quest.model.PlayerQuestComponent;
import rpg.quest.model.QuestReward;

/**
 * Grants every field of a {@link QuestReward} (SOW section 11) through orelia-core's
 * published API instead of quest re-implementing item/currency/skill logic or depending
 * on orelia-core's internal classes.
 */
public final class QuestRewardService {

    private final PlayerDataManager playerDataManager;
    private final StatusApi statusApi;
    private final Economy economy;
    private final ItemApi itemApi;
    private final AccessoryApi accessoryApi;
    private final SkillApi skillApi;

    public QuestRewardService(PlayerDataManager playerDataManager, StatusApi statusApi, Economy economy,
                               ItemApi itemApi, AccessoryApi accessoryApi, SkillApi skillApi) {
        this.playerDataManager = playerDataManager;
        this.statusApi = statusApi;
        this.economy = economy;
        this.itemApi = itemApi;
        this.accessoryApi = accessoryApi;
        this.skillApi = skillApi;
    }

    public void grant(Player player, QuestReward reward) {
        if (reward.getExp() > 0) {
            statusApi.addExperience(player.getUniqueId(), reward.getExp());
        }
        if (reward.getMoney() > 0 && economy != null) {
            economy.depositPlayer(player, reward.getMoney());
        }
        if (reward.getWeaponId() != null) {
            itemApi.createWeapon(reward.getWeaponId()).ifPresent(stack -> giveOrDrop(player, stack));
        }
        if (reward.getAccessoryId() != null) {
            accessoryApi.createAccessory(reward.getAccessoryId()).ifPresent(stack -> giveOrDrop(player, stack));
        }
        if (reward.getSkillPoints() > 0) {
            skillApi.grantSkillPoints(player.getUniqueId(), reward.getSkillPoints());
        }
        if (reward.getTitle() != null && !reward.getTitle().isBlank()) {
            playerDataManager.get(player.getUniqueId())
                    .flatMap(d -> d.component(PlayerQuestComponent.class))
                    .ifPresent(component -> component.addTitle(reward.getTitle()));
        }
        if (reward.getVanillaMaterial() != null && reward.getVanillaAmount() > 0) {
            try {
                giveOrDrop(player, new ItemStack(Material.valueOf(reward.getVanillaMaterial().trim().toUpperCase()), reward.getVanillaAmount()));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void giveOrDrop(Player player, ItemStack stack) {
        player.getInventory().addItem(stack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }
}
