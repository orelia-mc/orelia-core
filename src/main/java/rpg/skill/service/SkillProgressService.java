package rpg.skill.service;

import rpg.core.player.PlayerDataManager;
import rpg.skill.model.PlayerSkillComponent;
import rpg.skill.model.SkillData;
import rpg.skill.repository.SkillRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Learn/upgrade skills by spending skill points earned from quests (SOW section 11).
 */
public final class SkillProgressService {

    /** Outcome of {@link #upgradeSkill}, specific enough for the caller to show the right reason on failure. */
    public enum UpgradeResult {
        OK, UNKNOWN_SKILL, MAX_LEVEL, INSUFFICIENT_POINTS
    }

    private final PlayerDataManager playerDataManager;
    private final SkillRepository skillRepository;

    public SkillProgressService(PlayerDataManager playerDataManager, SkillRepository skillRepository) {
        this.playerDataManager = playerDataManager;
        this.skillRepository = skillRepository;
    }

    public int getSkillLevel(UUID uuid, String skillId) {
        return component(uuid).map(c -> c.getSkillLevel(skillId)).orElse(0);
    }

    public void grantSkillPoints(UUID uuid, int amount) {
        component(uuid).ifPresent(c -> c.addSkillPoints(amount));
    }

    public int getSkillPoints(UUID uuid) {
        return component(uuid).map(PlayerSkillComponent::getSkillPoints).orElse(0);
    }

    /** Subtracts {@code amount} skill points, failing (no change) rather than going negative. */
    public boolean takeSkillPoints(UUID uuid, int amount) {
        return component(uuid).map(c -> c.takeSkillPoints(amount)).orElse(false);
    }

    public void setSkillPoints(UUID uuid, int amount) {
        component(uuid).ifPresent(c -> c.setSkillPoints(amount));
    }

    /**
     * Spends one skill point to raise {@code skillId} by one level, capped at the skill's
     * {@code max-level}.
     */
    public UpgradeResult upgradeSkill(UUID uuid, String skillId) {
        Optional<SkillData> data = skillRepository.findById(skillId);
        Optional<PlayerSkillComponent> component = component(uuid);
        if (data.isEmpty() || component.isEmpty()) {
            return UpgradeResult.UNKNOWN_SKILL;
        }
        PlayerSkillComponent c = component.get();
        int current = c.getSkillLevel(skillId);
        if (current >= data.get().getMaxLevel()) {
            return UpgradeResult.MAX_LEVEL;
        }
        if (!c.spendSkillPoint()) {
            return UpgradeResult.INSUFFICIENT_POINTS;
        }
        c.setSkillLevel(skillId, current + 1);
        return UpgradeResult.OK;
    }

    private Optional<PlayerSkillComponent> component(UUID uuid) {
        return playerDataManager.get(uuid).flatMap(d -> d.component(PlayerSkillComponent.class));
    }
}
