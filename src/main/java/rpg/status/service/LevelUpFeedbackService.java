package rpg.status.service;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
import rpg.core.scheduler.SchedulerService;
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
 *
 * <p>Character level-up staggers title -&gt; chat -&gt; stat-diff lines across a couple of
 * short delays ({@code config.yml: status.level-up-effect.chat-delay-ticks}/
 * {@code stat-delay-ticks}) instead of sending everything in the same tick, so the sequence
 * reads as one connected moment rather than a wall of text appearing at once. Each delayed
 * stage re-resolves {@code Bukkit.getPlayer(uuid)} via {@link #withPlayer}, so a player who
 * logs out mid-sequence simply causes the remaining stages to no-op rather than throwing.
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
        STAT_LABELS.put(StatType.FIRE_DMG, "火属性ダメージ");
        STAT_LABELS.put(StatType.WATER_DMG, "水属性ダメージ");
        STAT_LABELS.put(StatType.EARTH_DMG, "土属性ダメージ");
        STAT_LABELS.put(StatType.WIND_DMG, "風属性ダメージ");
        STAT_LABELS.put(StatType.LIGHT_DMG, "光属性ダメージ");
        STAT_LABELS.put(StatType.DARK_DMG, "闇属性ダメージ");
    }

    private static final double DIFF_EPSILON = 1e-6;

    private final MessageManager messageManager;
    private final LevelUpEffectConfig effectConfig;
    private final SchedulerService schedulerService;

    public LevelUpFeedbackService(MessageManager messageManager, LevelUpEffectConfig effectConfig,
                                   SchedulerService schedulerService) {
        this.messageManager = messageManager;
        this.effectConfig = effectConfig;
        this.schedulerService = schedulerService;
    }

    /**
     * Character level-up: title/sound/particle immediately, then the "レベルが上がりました"
     * chat line after {@code chat-delay-ticks}, then the per-stat diff lines after a further
     * {@code stat-delay-ticks} - one connected sequence instead of everything landing at once.
     */
    public void announceCharacterLevelUp(UUID uuid, int newLevel, StatSheet oldStats, StatSheet newStats) {
        withPlayer(uuid, player -> {
            playEffects(player);
            showTitle(player, newLevel);
            schedulerService.runLater(() -> withPlayer(uuid, chatPlayer -> {
                messageManager.send(chatPlayer, "status.level-up-chat", "level", newLevel);
                schedulerService.runLater(() -> withPlayer(uuid, statPlayer -> sendStatLines(statPlayer, oldStats, newStats)),
                        effectConfig.getStatDelayTicks());
            }), effectConfig.getChatDelayTicks());
        });
    }

    /**
     * One chat line per raised stat. Tried collapsing this into a single action-bar line
     * instead, but the action bar can't wrap/break onto multiple lines - a level-up with
     * several raised stats just ran the whole summary together illegibly, so this stays in
     * chat (level-ups are rare enough that this doesn't repeat the "floods chat" problem the
     * boss/skill announcements had).
     */
    private void sendStatLines(Player player, StatSheet oldStats, StatSheet newStats) {
        for (StatType type : StatType.values()) {
            double diff = newStats.get(type) - oldStats.get(type);
            if (diff <= DIFF_EPSILON) {
                continue;
            }
            String label = STAT_LABELS.getOrDefault(type, type.name());
            player.sendMessage(ColorUtil.component(
                    messageManager.format("status.level-up-stat-line", "stat", label, "value", formatDiff(diff))));
        }
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
