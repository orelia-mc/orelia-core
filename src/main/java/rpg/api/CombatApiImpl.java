package rpg.api;

import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import rpg.boss.BossModule;
import rpg.boss.model.BossData;
import rpg.monster.service.MonsterSpawnService;

import java.util.Optional;

final class CombatApiImpl implements CombatApi {

    private final MonsterSpawnService monsterSpawnService;
    private final BossModule bossModule;

    CombatApiImpl(MonsterSpawnService monsterSpawnService, BossModule bossModule) {
        this.monsterSpawnService = monsterSpawnService;
        this.bossModule = bossModule;
    }

    @Override
    public Optional<String> identifyMonster(LivingEntity entity) {
        return monsterSpawnService.idOf(entity);
    }

    @Override
    public Optional<String> identifyBoss(String monsterId) {
        return bossModule.getRepository().findByMonsterId(monsterId).map(BossData::getId);
    }

    @Override
    public Optional<LivingEntity> spawnMonster(String monsterId, Location location, Integer targetLevel) {
        return monsterSpawnService.spawn(monsterId, location, null, targetLevel);
    }

    @Override
    public Optional<LivingEntity> spawnBoss(String bossId, Location location, Integer targetLevel) {
        return bossModule.spawn(bossId, location, targetLevel);
    }
}
