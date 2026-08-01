package rpg.status.service;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
import rpg.status.config.LevelUpEffectConfig;
import rpg.status.model.StatSheet;
import rpg.status.model.StatType;
import rpg.util.ColorUtil;

import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

/**
 * Plays the title/sound/particle/chat feedback for a level-up - shared between character
 * levels ({@link StatusService#addExperience}) and job/gathering levels
 * ({@code rpg.gathering.service.GatheringLevelService}) so both play the same effect and go
 * through {@code messages.yml} via {@link MessageManager} instead of hardcoded strings.
 */
public final class LevelUpFeedbackService {

    private static final Map<StatType, String> STAT_LABELS = new EnumMap<>(StatType.class);

    static {
        STAT_LABELS.put(StatType.HP, "HP");
        STAT_LABELS.put(StatType.SP, "SP");
        STAT_LABELS.put(StatType.ATK, "攻撃力");
        STAT_LABELS.put(StatType.DEF, "防御力");
        STAT_LABELS.put(StatType.SPD, "移動速度");
        STAT_LABELS.put(StatType.CRT, "会心率");
        STAT_LABELS.put(StatType.CRT_DMG, "会心ダメージ");
        STAT_LABELS.put(StatType.SP_RECOVERY, "SP回復効率");
    }

    private static final double DIFF_EPSILON = 1e-6;

    private final MessageManager messageManager;
    private final LevelUpEffectConfig effectConfig;

    public LevelUpFeedbackService(MessageManager messageManager, LevelUpEffectConfig effectConfig) {
        this.messageManager = messageManager;
        this.effectConfig = effectConfig;
    }

    /** Character level-up: title/sound/particle plus a chat line per stat that grew. */
    public void announceCharacterLevelUp(UUID uuid, int newLevel, StatSheet oldStats, StatSheet newStats) {
        withPlayer(uuid, player -> {
            playEffects(player);
            showTitle(player, newLevel);
            messageManager.send(player, "status.level-up-chat", "level", newLevel);
            for (StatType type : StatType.values()) {
                double diff = newStats.get(type) - oldStats.get(type);
                if (diff <= DIFF_EPSILON) {
                    continue;
                }
                String label = STAT_LABELS.getOrDefault(type, type.name());
                player.sendMessage(ColorUtil.component(
                        messageManager.format("status.level-up-stat-line", "stat", label, "value", formatDiff(diff))));
            }
        });
    }

    /** Job/gathering level-up: same title/sound/particle, caller supplies its own chat message key. */
    public void announceJobLevelUp(UUID uuid, String jobDisplayName, int newLevel) {
        withPlayer(uuid, player -> {
            playEffects(player);
            showTitle(player, newLevel);
            messageManager.send(player, "gathering.level-up", "job", jobDisplayName, "level", newLevel);
        });
    }

    private void showTitle(Player player, int newLevel) {
        player.showTitle(Title.title(
                ColorUtil.component(messageManager.raw("status.level-up-title")),
                ColorUtil.component(messageManager.format("status.level-up-subtitle", "level", newLevel)),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2000), Duration.ofMillis(500))));
    }

    private void playEffects(Player player) {
        if (!effectConfig.isEnabled()) {
            return;
        }
        try {
            Sound sound = Sound.valueOf(effectConfig.getSound());
            player.playSound(player.getLocation(), sound, 1f, 1f);
        } catch (IllegalArgumentException ignored) {
        }
        if (player.getWorld() != null) {
            try {
                Particle particle = Particle.valueOf(effectConfig.getParticle());
                player.getWorld().spawnParticle(particle, player.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.05);
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void withPlayer(UUID uuid, java.util.function.Consumer<Player> action) {
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            action.accept(player);
        }
    }

    private static String formatDiff(double diff) {
        return diff == Math.rint(diff) ? String.valueOf((long) diff) : String.format(java.util.Locale.ROOT, "%.1f", diff);
    }
}
