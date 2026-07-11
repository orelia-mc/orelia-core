package rpg.skill.service;

import org.bukkit.entity.Player;
import rpg.core.player.PlayerDataManager;
import rpg.item.service.WeaponIdentityService;
import rpg.skill.manager.SkillExecutorRegistry;
import rpg.skill.model.PlayerSkillComponent;
import rpg.skill.model.SkillData;
import rpg.skill.repository.SkillRepository;
import rpg.status.service.StatusService;

import java.util.Optional;

/**
 * Orchestrates one skill cast: weapon/socket/level/cooldown/SP checks, then delegates the
 * actual effect to the {@link rpg.skill.executor.SkillExecutor} the skill's config points at.
 */
public final class SkillCastService {

    public enum CastFailure {
        UNKNOWN_SKILL, WRONG_WEAPON, NOT_SOCKETED, NOT_LEARNED, ON_COOLDOWN, NOT_ENOUGH_SP, NO_EXECUTOR
    }

    private final PlayerDataManager playerDataManager;
    private final SkillRepository skillRepository;
    private final SkillExecutorRegistry executorRegistry;
    private final SkillSocketService socketService;
    private final WeaponIdentityService weaponIdentityService;
    private final StatusService statusService;

    public SkillCastService(PlayerDataManager playerDataManager, SkillRepository skillRepository,
                             SkillExecutorRegistry executorRegistry, SkillSocketService socketService,
                             WeaponIdentityService weaponIdentityService, StatusService statusService) {
        this.playerDataManager = playerDataManager;
        this.skillRepository = skillRepository;
        this.executorRegistry = executorRegistry;
        this.socketService = socketService;
        this.weaponIdentityService = weaponIdentityService;
        this.statusService = statusService;
    }

    public Optional<CastFailure> cast(Player caster, String skillId) {
        SkillData data = skillRepository.findById(skillId).orElse(null);
        if (data == null) {
            return Optional.of(CastFailure.UNKNOWN_SKILL);
        }

        boolean holdingRightWeapon = weaponIdentityService.dataOf(caster.getInventory().getItemInMainHand())
                .map(weapon -> weapon.getWeaponType() == data.getWeaponType())
                .orElse(false);
        if (!holdingRightWeapon) {
            return Optional.of(CastFailure.WRONG_WEAPON);
        }
        if (!socketService.hasSkill(caster.getInventory().getItemInMainHand(), skillId)) {
            return Optional.of(CastFailure.NOT_SOCKETED);
        }

        Optional<PlayerSkillComponent> componentOpt = playerDataManager.get(caster.getUniqueId())
                .flatMap(d -> d.component(PlayerSkillComponent.class));
        if (componentOpt.isEmpty()) {
            return Optional.of(CastFailure.NOT_LEARNED);
        }
        PlayerSkillComponent component = componentOpt.get();
        int skillLevel = component.getSkillLevel(skillId);
        if (skillLevel <= 0) {
            return Optional.of(CastFailure.NOT_LEARNED);
        }

        long now = System.currentTimeMillis();
        if (component.isOnCooldown(skillId, now)) {
            return Optional.of(CastFailure.ON_COOLDOWN);
        }
        if (!statusService.tryConsumeSp(caster.getUniqueId(), data.getSpCost())) {
            return Optional.of(CastFailure.NOT_ENOUGH_SP);
        }

        var executor = executorRegistry.get(data.getExecutorType());
        if (executor.isEmpty()) {
            return Optional.of(CastFailure.NO_EXECUTOR);
        }

        component.setCooldown(skillId, now + (long) (data.getCooldownSeconds() * 1000));
        executor.get().execute(caster, data, skillLevel);
        return Optional.empty();
    }
}
