package rpg.relic.gui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import rpg.core.message.MessageManager;
import rpg.relic.model.RelicInstance;
import rpg.relic.model.RelicStatType;
import rpg.relic.service.RelicIdentityService;
import rpg.relic.service.RelicUpgradeService;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.util.ItemBuilder;

import java.util.List;
import java.util.Optional;

/**
 * Lets the player pick which substat to add/grow on the relic currently in their main hand
 * (see {@link RelicUpgradeService} for why this is a choice rather than a random roll).
 * Opened by {@code /ol relic upgrade}.
 */
public final class RelicUpgradeGuiScreen {

    private final RelicIdentityService identityService;
    private final RelicUpgradeService upgradeService;
    private final MessageManager messages;

    public RelicUpgradeGuiScreen(RelicIdentityService identityService, RelicUpgradeService upgradeService, MessageManager messages) {
        this.identityService = identityService;
        this.upgradeService = upgradeService;
        this.messages = messages;
    }

    public Optional<Gui> build(Player player, ItemStack relicStack) {
        Optional<RelicInstance> instanceOpt = identityService.read(relicStack);
        if (instanceOpt.isEmpty()) {
            return Optional.empty();
        }
        RelicInstance instance = instanceOpt.get();
        Gui gui = new Gui("&%8レリック厳選", 27);
        if (instance.level() >= 15) {
            gui.set(13, GuiButton.display(new ItemBuilder(Material.BARRIER).name("&%c最大レベルです").build()));
            return Optional.of(gui);
        }

        double cost = upgradeService.nextUpgradeCost(instance).orElse(0.0);
        List<RelicStatType> choices = upgradeService.availableChoices(instance);
        int slot = 10;
        for (RelicStatType choice : choices) {
            boolean isNewLine = instance.substats().stream().noneMatch(line -> line.type() == choice);
            gui.set(slot++, new GuiButton(new ItemBuilder(Material.NETHER_STAR)
                    .name("&%e" + choice.getDisplayLabel())
                    .lore(List.of(
                            isNewLine ? "&%7新規ステータスとして追加" : "&%7既存ステータスを強化",
                            "&%7費用: &%f" + (long) cost))
                    .build(), (clicker, clickType) -> handleChoice(clicker, relicStack, instance, choice)));
        }
        return Optional.of(gui);
    }

    private void handleChoice(Player player, ItemStack relicStack, RelicInstance instance, RelicStatType choice) {
        player.closeInventory();
        Optional<RelicUpgradeService.UpgradeFailure> failure = upgradeService.upgrade(player, relicStack, instance, choice);
        if (failure.isPresent()) {
            String key = switch (failure.get()) {
                case MAX_LEVEL -> "relic.upgrade-max-level";
                case INVALID_CHOICE -> "relic.upgrade-invalid-choice";
                case INSUFFICIENT_FUNDS -> "relic.upgrade-insufficient-funds";
            };
            messages.send(player, key);
            return;
        }
        // getItemInMainHand() may return a defensive copy on some implementations - write it
        // back explicitly so the upgraded PDC/lore is guaranteed to stick on the held item.
        player.getInventory().setItemInMainHand(relicStack);
        messages.send(player, "relic.upgrade-success", "stat", choice.getDisplayLabel());
    }
}
