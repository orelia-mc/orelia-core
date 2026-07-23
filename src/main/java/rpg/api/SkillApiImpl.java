package rpg.api;

import rpg.skill.model.SkillData;
import rpg.skill.repository.SkillRepository;
import rpg.skill.service.SkillProgressService;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class SkillApiImpl implements SkillApi {

    private final SkillProgressService progressService;
    private final SkillRepository skillRepository;

    SkillApiImpl(SkillProgressService progressService, SkillRepository skillRepository) {
        this.progressService = progressService;
        this.skillRepository = skillRepository;
    }

    @Override
    public void grantSkillPoints(UUID playerId, int amount) {
        progressService.grantSkillPoints(playerId, amount);
    }

    @Override
    public int getSkillPoints(UUID playerId) {
        return progressService.getSkillPoints(playerId);
    }

    @Override
    public boolean takeSkillPoints(UUID playerId, int amount) {
        return progressService.takeSkillPoints(playerId, amount);
    }

    @Override
    public void setSkillPoints(UUID playerId, int amount) {
        progressService.setSkillPoints(playerId, amount);
    }

    @Override
    public int getSkillLevel(UUID playerId, String skillId) {
        return progressService.getSkillLevel(playerId, skillId);
    }

    @Override
    public List<SkillSummary> getLearnedSkills(UUID playerId) {
        List<SkillSummary> learned = new ArrayList<>();
        for (SkillData skill : skillRepository.getAll().values()) {
            int level = progressService.getSkillLevel(playerId, skill.getId());
            if (level > 0) {
                learned.add(new SkillSummary(skill.getId(), skill.getName(), level, skill.getMaxLevel()));
            }
        }
        return learned;
    }
}
