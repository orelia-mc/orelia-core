package rpg.boss.service;

import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import rpg.monster.model.MonsterData;
import rpg.monster.service.MonsterSpawnService;
import rpg.util.ColorUtil;
import rpg.util.MathUtil;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shows a vanilla boss bar (HP progress) to any player within {@link #VISIBILITY_RADIUS}
 * blocks of an active boss, syncing both progress and viewer membership every {@link #tick()}.
 * Reuses {@link MonsterSpawnService}'s scaled current/max HP - the same numbers the nametag
 * health bar renders - so the two stay consistent with each other.
 */
public final class BossBarService {

    private static final double VISIBILITY_RADIUS = 7.0;

    private record Entry(BossBar bar, Set<UUID> viewerIds) {
    }

    private final MonsterSpawnService spawnService;
    private final Map<UUID, Entry> activeBars = new ConcurrentHashMap<>();

    public BossBarService(MonsterSpawnService spawnService) {
        this.spawnService = spawnService;
    }

    public void register(LivingEntity boss, MonsterData data) {
        BossBar bar = BossBar.bossBar(ColorUtil.component("&%c" + data.getName()), 1.0f, BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        activeBars.put(boss.getUniqueId(), new Entry(bar, new HashSet<>()));
    }

    /** Call periodically (e.g. every 10 ticks). Drops any boss that died/despawned since the last tick. */
    public void tick() {
        Iterator<Map.Entry<UUID, Entry>> iterator = activeBars.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Entry> entry = iterator.next();
            LivingEntity boss = resolveLiving(entry.getKey());
            MonsterData data = boss == null ? null : spawnService.dataOf(boss).orElse(null);
            if (boss == null || boss.isDead() || !boss.isValid() || data == null) {
                hideFromAllViewers(entry.getValue());
                iterator.remove();
                continue;
            }
            sync(boss, data, entry.getValue());
        }
    }

    private void sync(LivingEntity boss, MonsterData data, Entry entry) {
        double max = spawnService.scaledMaxHpOf(boss, data);
        double current = spawnService.scaledCurrentHpOf(boss, data);
        entry.bar().progress((float) MathUtil.clamp(max <= 0 ? 0 : current / max, 0, 1));

        Set<UUID> nearbyIds = new HashSet<>();
        for (Player player : boss.getWorld().getNearbyPlayers(boss.getLocation(), VISIBILITY_RADIUS)) {
            nearbyIds.add(player.getUniqueId());
            if (entry.viewerIds().add(player.getUniqueId())) {
                player.showBossBar(entry.bar());
            }
        }
        entry.viewerIds().removeIf(id -> {
            if (nearbyIds.contains(id)) {
                return false;
            }
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.hideBossBar(entry.bar());
            }
            return true;
        });
    }

    private void hideFromAllViewers(Entry entry) {
        for (UUID id : entry.viewerIds()) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                player.hideBossBar(entry.bar());
            }
        }
    }

    private LivingEntity resolveLiving(UUID id) {
        Entity entity = Bukkit.getEntity(id);
        return entity instanceof LivingEntity living ? living : null;
    }
}
