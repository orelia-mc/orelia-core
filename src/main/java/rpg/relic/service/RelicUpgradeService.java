package rpg.relic.service;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import rpg.item.model.ElementType;
import rpg.relic.config.RelicConfig;
import rpg.relic.model.RelicInstance;
import rpg.relic.model.RelicLine;
import rpg.relic.model.RelicStatType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * "選べる厳選" - the deliberate differentiator from a pure-RNG artifact system: a relic starts
 * with 0 substats, and every 3 levels (5 times across level 0-15) the player picks *which*
 * stat grows, not just how much. Below 4 substat lines, only "add a new line" is offered;
 * once all 4 are filled, only "grow an existing line" is offered - see
 * {@link #availableChoices}. The +1~2 magnitude per pick stays randomized, keeping a small
 * amount of luck without making the stat itself a gamble.
 */
public final class RelicUpgradeService {

    public enum UpgradeFailure {
        MAX_LEVEL, INVALID_CHOICE, INSUFFICIENT_FUNDS
    }

    private static final int MAX_LEVEL = 15;
    private static final int LEVELS_PER_UPGRADE = 3;
    private static final int MAX_SUBSTATS = 4;

    private final RelicConfig config;
    private final RelicIdentityService identityService;
    private final RelicFactory factory;
    private final Economy economy;
    private final Random random = new Random();

    public RelicUpgradeService(RelicConfig config, RelicIdentityService identityService, RelicFactory factory, Economy economy) {
        this.config = config;
        this.identityService = identityService;
        this.factory = factory;
        this.economy = economy;
    }

    /** Money cost of the next upgrade, or empty if already at {@link #MAX_LEVEL}. */
    public Optional<Double> nextUpgradeCost(RelicInstance instance) {
        if (instance.level() >= MAX_LEVEL) {
            return Optional.empty();
        }
        return Optional.of(config.getUpgradeCostBase() + config.getUpgradeCostPerLevel() * instance.level());
    }

    /**
     * The stat types the player may pick for the next upgrade: new lines (excluding the main
     * stat's type, {@code ELEMENTAL_DMG_PERCENT}, and any already-owned substat) while under
     * {@link #MAX_SUBSTATS} lines, otherwise the already-owned lines themselves (to grow).
     */
    public List<RelicStatType> availableChoices(RelicInstance instance) {
        Set<RelicStatType> owned = instance.substats().stream().map(RelicLine::type).collect(Collectors.toSet());
        if (owned.size() >= MAX_SUBSTATS) {
            return List.copyOf(owned);
        }
        return Arrays.stream(RelicStatType.values())
                .filter(type -> type != RelicStatType.ELEMENTAL_DMG_PERCENT)
                .filter(type -> type != instance.mainStat().type())
                .filter(type -> !owned.contains(type))
                .toList();
    }

    public Optional<UpgradeFailure> upgrade(Player player, ItemStack stack, RelicInstance instance, RelicStatType chosen) {
        if (instance.level() >= MAX_LEVEL) {
            return Optional.of(UpgradeFailure.MAX_LEVEL);
        }
        if (!availableChoices(instance).contains(chosen)) {
            return Optional.of(UpgradeFailure.INVALID_CHOICE);
        }
        double cost = config.getUpgradeCostBase() + config.getUpgradeCostPerLevel() * instance.level();
        if (economy == null || !economy.has(player, cost) || !economy.withdrawPlayer(player, cost).transactionSuccess()) {
            return Optional.of(UpgradeFailure.INSUFFICIENT_FUNDS);
        }

        double gained = round1(1 + random.nextDouble());
        List<RelicLine> substats = new ArrayList<>(instance.substats());
        int existingIndex = -1;
        for (int i = 0; i < substats.size(); i++) {
            if (substats.get(i).type() == chosen) {
                existingIndex = i;
                break;
            }
        }
        if (existingIndex >= 0) {
            RelicLine current = substats.get(existingIndex);
            substats.set(existingIndex, new RelicLine(chosen, current.element(), round1(current.value() + gained)));
        } else {
            // Substats never roll ELEMENTAL_DMG_PERCENT (see availableChoices), so element is always NONE here.
            substats.add(new RelicLine(chosen, ElementType.NONE, gained));
        }

        RelicInstance updated = instance.withSubstats(substats).withLevel(instance.level() + LEVELS_PER_UPGRADE);
        identityService.write(stack, updated);
        factory.refreshLore(stack, updated);
        return Optional.empty();
    }

    private double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
