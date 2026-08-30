package rpg.quest.service;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import rpg.core.module.ModuleManager;
import rpg.dungeon.DungeonModule;
import rpg.item.ItemModule;
import rpg.item.model.WeaponData;
import rpg.monster.MonsterModule;
import rpg.npc.NpcModule;
import rpg.quest.model.QuestObjective;
import rpg.util.ColorUtil;

import java.util.Optional;

/**
 * Resolves a {@link QuestObjective}'s raw {@code targetId} (a monsters.yml/items.yml/npc.yml/
 * dungeons.yml id, or a vanilla Material name) down to a player-facing label - shared by
 * {@code rpg.quest.gui.QuestGuiScreen} and {@code rpg.world.playerinfo.gui.PlayerInfoQuestGuiScreen}
 * so a quest log never shows a raw id to a player (this used to be the case for every objective
 * type - e.g. "モンスター討伐 (forest_slime)" instead of a monster's actual name).
 *
 * <p>Every module is resolved lazily via {@link ModuleManager} on each call rather than once at
 * construction time. {@link MonsterModule}/{@link ItemModule} would actually be safe to take
 * eagerly (foundation-block, registered before Quest), but {@link NpcModule}/{@link DungeonModule}
 * are not - {@code QuestModule} registers *before* both of them in the fixed module order, so a
 * lookup at construction would always resolve empty even though every one of the four is fully
 * registered by the time a player actually opens their quest log. Kept uniformly lazy across all
 * four rather than mixing eager/lazy lookups, same reasoning {@code PlayerInfoGuiScreen}'s own
 * Javadoc gives for its social-shortcut buttons.
 */
public final class QuestObjectiveDescriber {

    private final ModuleManager moduleManager;

    public QuestObjectiveDescriber(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    /** {@link Component#empty()} for {@code REACH_LOCATION} (no meaningful target id) or a {@code null} targetId. */
    public Component targetLabel(QuestObjective objective) {
        String targetId = objective.getTargetId();
        if (targetId == null) {
            return Component.empty();
        }
        return switch (objective.getType()) {
            case KILL_MONSTER, KILL_BOSS -> moduleManager.get(MonsterModule.class)
                    .flatMap(module -> module.getRepository().findById(targetId))
                    .<Component>map(data -> ColorUtil.component(data.getName()))
                    .orElseGet(() -> Component.text(targetId));
            case COLLECT_ITEM, DELIVER_ITEM -> itemLabel(targetId);
            case TALK_NPC -> moduleManager.get(NpcModule.class)
                    .flatMap(module -> module.getRepository().findById(targetId))
                    .<Component>map(data -> ColorUtil.component(data.getName()))
                    .orElseGet(() -> Component.text(targetId));
            case CLEAR_DUNGEON -> moduleManager.get(DungeonModule.class)
                    .flatMap(module -> module.getRepository().findById(targetId))
                    .<Component>map(data -> ColorUtil.component(data.getName()))
                    .orElseGet(() -> Component.text(targetId));
            case REACH_LOCATION -> Component.empty();
        };
    }

    /** An items.yml weapon-id resolves to its configured name; a vanilla Material id resolves client-side via a translatable component - see {@code QuestItemInventoryService#matches}, which accepts either for the same objective. */
    private Component itemLabel(String targetId) {
        Optional<WeaponData> weapon = moduleManager.get(ItemModule.class).flatMap(module -> module.getItemManager().findById(targetId));
        if (weapon.isPresent()) {
            return ColorUtil.component(weapon.get().getName());
        }
        return materialOf(targetId).<Component>map(material -> Component.translatable(material.translationKey()))
                .orElseGet(() -> Component.text(targetId));
    }

    private Optional<Material> materialOf(String id) {
        try {
            return Optional.of(Material.valueOf(id.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
