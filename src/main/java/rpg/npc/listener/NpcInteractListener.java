package rpg.npc.listener;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import rpg.api.GuiApi;
import rpg.api.ItemApi;
import rpg.api.RelicApi;
import rpg.core.message.MessageManager;
import rpg.gui.framework.GuiManager;
import rpg.npc.event.NpcGuildInteractEvent;
import rpg.npc.model.NpcData;
import rpg.npc.service.NpcSpawnService;
import rpg.quest.gui.QuestGuiScreen;
import rpg.quest.service.QuestProgressService;
import rpg.util.ColorUtil;

import java.util.List;

/**
 * Dispatches an NPC click to the right screen/action for its {@link rpg.npc.model.NpcType}.
 * Shop/job-change/warehouse screens are core-owned and opened through {@link GuiApi}; the
 * quest screen is opened locally since Quest lives in orelia-world too.
 */
public final class NpcInteractListener implements Listener {

    private final NpcSpawnService spawnService;
    private final GuiApi guiApi;
    private final GuiManager guiManager;
    private final QuestGuiScreen questGuiScreen;
    private final QuestProgressService questProgressService;
    private final ItemApi itemApi;
    private final RelicApi relicApi;
    private final Economy economy;
    private final MessageManager messages;

    public NpcInteractListener(NpcSpawnService spawnService, GuiApi guiApi, GuiManager guiManager,
                                QuestGuiScreen questGuiScreen, QuestProgressService questProgressService,
                                ItemApi itemApi, RelicApi relicApi, Economy economy, MessageManager messages) {
        this.spawnService = spawnService;
        this.guiApi = guiApi;
        this.guiManager = guiManager;
        this.questGuiScreen = questGuiScreen;
        this.questProgressService = questProgressService;
        this.itemApi = itemApi;
        this.relicApi = relicApi;
        this.economy = economy;
        this.messages = messages;
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        NpcData data = spawnService.dataOf(event.getRightClicked()).orElse(null);
        if (data == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();

        sendDialogue(player, data);
        questProgressService.onNpcTalked(player.getUniqueId(), data.getId());

        switch (data.getType()) {
            case WEAPON_SHOP, ARMOR_SHOP, ACCESSORY_SHOP -> guiApi.openShop(player, data.getShopStock());
            case QUEST_RECEPTIONIST -> guiManager.open(player, questGuiScreen.build(player, data.getQuestIds()));
            case JOB_CHANGE -> guiApi.openJobChange(player);
            case WAREHOUSE -> guiApi.openWarehouse(player);
            case ENHANCEMENT -> enhance(player, data);
            case WEAPON_LEVELUP -> levelUpWeapon(player, data);
            case RELIC_UPGRADE -> relicApi.openUpgradeGui(player);
            // orelia-world can't compile-depend on orelia-extra (guild lives there), so this
            // just fires a hook event - a harmless no-op unless orelia-extra is installed and
            // listening (see rpg.extra.guild.listener.NpcGuildInteractListener).
            case GUILD_RECEPTIONIST -> Bukkit.getPluginManager().callEvent(new NpcGuildInteractEvent(player, data));
        }
    }

    private void enhance(Player player, NpcData data) {
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (itemApi.identifyWeapon(weapon).isEmpty()) {
            messages.send(player, "npc.enhancement-need-weapon");
            return;
        }
        int currentLevel = itemApi.getEnhancementLevel(weapon);
        double cost = data.getEnhancementCostBase() + data.getEnhancementCostPerLevel() * currentLevel;
        if (economy == null || !economy.has(player, cost) || !economy.withdrawPlayer(player, cost).transactionSuccess()) {
            messages.send(player, "npc.enhancement-insufficient-funds", "cost", cost);
            return;
        }
        int newLevel = itemApi.enhanceWeapon(weapon);
        messages.send(player, "npc.enhancement-success", "level", newLevel);
    }

    private void levelUpWeapon(Player player, NpcData data) {
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (itemApi.identifyWeapon(weapon).isEmpty()) {
            messages.send(player, "npc.weapon-levelup-need-weapon");
            return;
        }
        int currentLevel = itemApi.getWeaponLevel(weapon);
        int cap = itemApi.getWeaponLevelCap(player.getUniqueId());
        if (currentLevel >= cap) {
            messages.send(player, "npc.weapon-levelup-cap-reached", "level", currentLevel);
            return;
        }
        Material material;
        try {
            material = Material.valueOf(data.getWeaponLevelupItemMaterial().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return; // misconfigured npc.yml entry - fail closed
        }
        int amount = data.getWeaponLevelupItemAmount();
        if (!player.getInventory().containsAtLeast(new ItemStack(material), amount)) {
            messages.send(player, "npc.weapon-levelup-insufficient-material", "amount", amount, "material", prettifyMaterialName(material));
            return;
        }
        double cost = data.getWeaponLevelupCostBase() + data.getWeaponLevelupCostPerLevel() * currentLevel;
        if (economy == null || !economy.has(player, cost)) {
            messages.send(player, "npc.weapon-levelup-insufficient-money", "cost", cost);
            return;
        }
        player.getInventory().removeItem(new ItemStack(material, amount));
        economy.withdrawPlayer(player, cost);
        int newLevel = itemApi.levelUpWeapon(player.getUniqueId(), weapon);
        if (newLevel < 0) {
            // shouldn't happen given the pre-check above, but don't silently swallow a real failure
            messages.send(player, "npc.weapon-levelup-cap-reached", "level", currentLevel);
            return;
        }
        itemApi.refreshWeaponLore(weapon);
        messages.send(player, "npc.weapon-levelup-success", "level", newLevel);
    }

    /** "DIAMOND_SWORD" -> "Diamond Sword" - never show a raw Material enum constant to a player. */
    private String prettifyMaterialName(Material material) {
        String[] words = material.name().toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }

    private void sendDialogue(Player player, NpcData data) {
        List<String> lines = data.getDialogueLines();
        if (data.getConditionalItemId() != null && !data.getConditionalDialogueLines().isEmpty()
                && hasItem(player, data.getConditionalItemId())) {
            lines = data.getConditionalDialogueLines();
        }
        for (String line : lines) {
            player.sendMessage(ColorUtil.colorize("&%f[" + data.getName() + "] &%7" + line));
        }
    }

    private boolean hasItem(Player player, String itemId) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null) {
                continue;
            }
            if (itemApi.identifyWeapon(stack).map(itemId::equals).orElse(false)) {
                return true;
            }
            try {
                if (stack.getType() == Material.valueOf(itemId.trim().toUpperCase())) {
                    return true;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return false;
    }
}
