package rpg.dungeon.service;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicesManager;
import org.bukkit.scheduler.BukkitTask;
import rpg.api.CombatApi;
import rpg.api.RelicApi;
import rpg.api.StatusApi;
import rpg.core.config.ConfigManager;
import rpg.core.message.MessageManager;
import rpg.core.player.PlayerDataManager;
import rpg.core.scheduler.SchedulerService;
import rpg.dungeon.manager.DungeonInstanceManager;
import rpg.dungeon.model.DungeonArena;
import rpg.dungeon.model.DungeonData;
import rpg.dungeon.model.DungeonEndReason;
import rpg.dungeon.model.DungeonInstance;
import rpg.dungeon.model.PlayerDungeonComponent;
import rpg.dungeon.repository.PlayerDungeonRepository;
import rpg.extra.api.PartyApi;
import rpg.extra.chat.NotificationSoundPlayer;
import rpg.quest.service.QuestProgressService;
import rpg.util.ColorUtil;
import rpg.util.MoneyFormat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

/**
 * Drives a dungeon run's live encounter on top of {@link DungeonService}'s bare
 * teleport-in/teleport-out/reward shell: spawning the configured enemies/boss, tracking
 * which ones are still alive, the time-limit countdown, and manual retire. Composes
 * {@link DungeonService} rather than extending it - that class's own job (party-size
 * validation, teleport, reward) stays a clean, narrow unit.
 */
public final class DungeonEncounterService {

    public enum ChallengeFailure {
        NOT_UNLOCKED, UNKNOWN_DUNGEON, PARTY_TOO_SMALL, PARTY_TOO_LARGE, WORLD_NOT_FOUND, ALREADY_IN_DUNGEON,
        DUNGEON_FULL, INVALID_DIFFICULTY
    }

    /** Fixed difficulty tiers a player may challenge at, each an upper bound on their own character level. */
    public static final List<Integer> DIFFICULTY_TIERS = List.of(5, 10, 15, 20, 30, 40, 50, 60, 70, 80, 90, 100);

    private static final double SPAWN_JITTER_RADIUS = 2.5;

    private static final int DEFAULT_RELIC_DROP_MIN = 1;
    private static final int DEFAULT_RELIC_DROP_MAX = 3;

    /** Delay between a successful arena reservation and the actual teleport/enemy spawn - the "10秒後に挑戦" warmup. */
    private static final long ENTRY_COUNTDOWN_TICKS = 200L;
    // Re-shown every second by the countdown timer below - fade-in/out kept short relative to
    // the 1-second tick interval so consecutive titles replace each other cleanly with no gap.
    private static final Title.Times COUNTDOWN_TITLE_TIMES =
            Title.Times.times(Duration.ofMillis(50), Duration.ofMillis(900), Duration.ofMillis(100));

    private final DungeonService dungeonService;
    private final DungeonInstanceManager instanceManager;
    private final CombatApi combatApi;
    private final RelicApi relicApi;
    private final StatusApi statusApi;
    private final SchedulerService schedulerService;
    private final ConfigManager configManager;
    private final PlayerDungeonRepository playerDungeonRepository;
    private final PlayerDataManager playerDataManager;
    private final ServicesManager services;
    private final QuestProgressService questProgressService;
    private final MessageManager messages;
    private final Logger logger;
    private final Random random = new Random();

    /** The party a challenge is resolved against: who must have the dungeon unlocked, and who actually enters. */
    private record PartyResolution(UUID leaderId, List<Player> members) {
    }

    public DungeonEncounterService(DungeonService dungeonService, DungeonInstanceManager instanceManager,
                                    CombatApi combatApi, RelicApi relicApi, StatusApi statusApi, SchedulerService schedulerService,
                                    ConfigManager configManager, PlayerDungeonRepository playerDungeonRepository,
                                    PlayerDataManager playerDataManager, ServicesManager services,
                                    QuestProgressService questProgressService, MessageManager messages, Logger logger) {
        this.dungeonService = dungeonService;
        this.instanceManager = instanceManager;
        this.combatApi = combatApi;
        this.relicApi = relicApi;
        this.statusApi = statusApi;
        this.schedulerService = schedulerService;
        this.configManager = configManager;
        this.playerDungeonRepository = playerDungeonRepository;
        this.playerDataManager = playerDataManager;
        this.services = services;
        this.questProgressService = questProgressService;
        this.messages = messages;
        this.logger = logger;
    }

    /** {@code difficulty}-less challenge (unscaled enemies) - see {@link #challenge(Player, String, Integer)}. */
    public Optional<ChallengeFailure> challenge(Player initiator, String dungeonId) {
        return challenge(initiator, dungeonId, null);
    }

    /**
     * Attempts to start a dungeon run for {@code initiator}'s real party (see
     * {@link #resolveParty}) - a solo player if they aren't in one. Only the party leader
     * needs to have unlocked the dungeon; other online members ride along. {@code difficulty}
     * (one of {@link #DIFFICULTY_TIERS}, capped at {@code initiator}'s own character level, or
     * {@code null} for unscaled) is validated against {@code initiator} regardless of who the
     * resolved party leader is - it's the challenger's own choice, not the leader's.
     *
     * <p>On success, the arena is reserved and the party's return locations captured
     * immediately (so a 4th concurrent party is still rejected right away as
     * {@link ChallengeFailure#DUNGEON_FULL}), but the actual teleport/enemy spawn happens
     * {@link #ENTRY_COUNTDOWN_TICKS} later - see {@link #enterInstance}.
     */
    public Optional<ChallengeFailure> challenge(Player initiator, String dungeonId, Integer difficulty) {
        if (difficulty != null) {
            int level = statusApi.getLevel(initiator.getUniqueId()).orElse(1);
            if (!DIFFICULTY_TIERS.contains(difficulty) || difficulty > level) {
                return Optional.of(ChallengeFailure.INVALID_DIFFICULTY);
            }
        }
        PartyResolution resolution = resolveParty(initiator);
        if (!isUnlockedForLeader(resolution.leaderId(), dungeonId)) {
            return Optional.of(ChallengeFailure.NOT_UNLOCKED);
        }

        Optional<DungeonService.StartFailure> failure = dungeonService.start(dungeonId, resolution.members());
        if (failure.isPresent()) {
            return Optional.of(mapFailure(failure.get()));
        }

        DungeonInstance instance = instanceManager.getByPlayer(initiator.getUniqueId()).orElseThrow();
        startEntryCountdown(instance, difficulty);
        return Optional.empty();
    }

    /**
     * Announces the entry countdown once per second (was a single "N秒後に挑戦します" message
     * at the start, with nothing after it until the teleport) so players get a clear beat
     * leading up to entry, then spawns the encounter once it reaches zero. Shown as a Title
     * rather than a chat line - a fresh chat message every second for several seconds straight
     * buried everything else being said, and a countdown is exactly the kind of transient,
     * glanceable cue Title/ActionBar exist for (same reasoning as level-up/quest-objective
     * feedback elsewhere in this codebase).
     */
    private void startEntryCountdown(DungeonInstance instance, Integer difficulty) {
        long[] secondsRemaining = {ENTRY_COUNTDOWN_TICKS / 20L};
        announceCountdown(instance, secondsRemaining[0]);
        AtomicReference<BukkitTask> taskRef = new AtomicReference<>();
        taskRef.set(schedulerService.runTimer(() -> {
            secondsRemaining[0]--;
            if (secondsRemaining[0] <= 0) {
                taskRef.get().cancel();
                enterInstance(instance, difficulty);
                return;
            }
            announceCountdown(instance, secondsRemaining[0]);
        }, 20L, 20L));
    }

    private void announceCountdown(DungeonInstance instance, long secondsRemaining) {
        Title title = Title.title(
                ColorUtil.component(messages.format("dungeon.entry-countdown-title", "seconds", secondsRemaining)),
                ColorUtil.component(messages.raw("dungeon.entry-countdown-subtitle")),
                COUNTDOWN_TITLE_TIMES);
        var config = configManager.get("config.yml").get();
        for (UUID memberId : instance.getMembers().keySet()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                member.showTitle(title);
                NotificationSoundPlayer.play(member, config.getBoolean("dungeon.entry-countdown-sound.enabled", true),
                        config.getString("dungeon.entry-countdown-sound.name", "BLOCK_NOTE_BLOCK_HAT"),
                        config.getDouble("dungeon.entry-countdown-sound.volume", 1.0),
                        config.getDouble("dungeon.entry-countdown-sound.pitch", 1.0), logger);
            }
        }
    }

    /**
     * Fires {@link #ENTRY_COUNTDOWN_TICKS} after a successful {@link #challenge} - teleports in
     * and spawns the encounter, unless every queued member has since logged out, in which case
     * the reservation is simply released instead of spawning enemies into an empty arena.
     */
    private void enterInstance(DungeonInstance instance, Integer difficulty) {
        boolean anyoneOnline = instance.getMembers().keySet().stream().anyMatch(id -> Bukkit.getPlayer(id) != null);
        if (!anyoneOnline) {
            instanceManager.remove(instance.getId());
            return;
        }
        dungeonService.teleportIn(instance);
        playEntryStartSound(instance);
        spawnEncounter(instance, difficulty);
        scheduleTimeout(instance);
    }

    /** Plays once, right as the party is teleported in and enemies spawn - a distinct cue from the per-second {@link #announceCountdown} tick sound. */
    private void playEntryStartSound(DungeonInstance instance) {
        var config = configManager.get("config.yml").get();
        for (UUID memberId : instance.getMembers().keySet()) {
            Player member = Bukkit.getPlayer(memberId);
            if (member != null) {
                NotificationSoundPlayer.play(member, config.getBoolean("dungeon.entry-start-sound.enabled", true),
                        config.getString("dungeon.entry-start-sound.name", "ENTITY_ENDER_DRAGON_GROWL"),
                        config.getDouble("dungeon.entry-start-sound.volume", 1.0),
                        config.getDouble("dungeon.entry-start-sound.pitch", 1.0), logger);
            }
        }
    }

    /** Manually ends the run the given player is currently in. Returns false if they aren't in one. */
    public boolean retire(Player player) {
        return instanceManager.getByPlayer(player.getUniqueId())
                .map(instance -> {
                    forceEnd(instance, DungeonEndReason.RETIRED);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Debug helper: starts a solo run for {@code player} bypassing the unlock check (party-size
     * validation against {@code min-party-size} still applies via {@link DungeonService#start},
     * since a single player is the whole "party" here - a dungeon requiring more than one
     * member will still fail with {@link ChallengeFailure#PARTY_TOO_SMALL}). Unlike
     * {@link #challenge}, skips the entry countdown entirely - teleports and spawns immediately,
     * since this is a testplay convenience, not a real challenge flow.
     */
    public Optional<ChallengeFailure> forceStart(Player player, String dungeonId) {
        Optional<DungeonService.StartFailure> failure = dungeonService.start(dungeonId, List.of(player));
        if (failure.isPresent()) {
            return Optional.of(mapFailure(failure.get()));
        }
        DungeonInstance instance = instanceManager.getByPlayer(player.getUniqueId()).orElseThrow();
        dungeonService.teleportIn(instance);
        spawnEncounter(instance, null);
        scheduleTimeout(instance);
        return Optional.empty();
    }

    /** The dungeon id of the instance the given player is currently in, if any. */
    public Optional<String> getActiveDungeonId(UUID playerId) {
        return instanceManager.getByPlayer(playerId).map(instance -> instance.getData().getId());
    }

    /** Called by {@code DungeonMobDeathListener} whenever any entity dies - a no-op if it wasn't a tracked dungeon mob. */
    public void onMobDeath(UUID entityId) {
        instanceManager.getByMonster(entityId).ifPresent(instance -> {
            if (instance.untrackMonster(entityId) && instance.isCleared()) {
                forceEnd(instance, DungeonEndReason.CLEARED);
            }
        });
    }

    /**
     * Whether {@code leaderId} has discovered this dungeon. Prefers the in-memory
     * {@link PlayerDungeonComponent} when the leader is online - {@link PlayerDungeonRepository}'s
     * DB row only reflects a fresh {@code unlock()} once the periodic autosave or quit-save
     * runs, so a leader challenging immediately after discovering their own dungeon (the most
     * common case: a solo player, or a party leader) would otherwise see a stale "not unlocked"
     * result. Falls back to the DB only when the leader isn't online to have an in-memory copy.
     */
    private boolean isUnlockedForLeader(UUID leaderId, String dungeonId) {
        Optional<PlayerDungeonComponent> online = playerDataManager.get(leaderId)
                .flatMap(data -> data.component(PlayerDungeonComponent.class));
        if (online.isPresent()) {
            return online.get().isUnlocked(dungeonId);
        }
        return playerDungeonRepository.isUnlocked(leaderId, dungeonId);
    }

    /**
     * Resolves who a challenge is judged against: the real party from orelia-extra's
     * {@link PartyApi} (leader's unlock status gates entry, every online member rides along),
     * falling back to a solo party of just {@code initiator} when orelia-extra isn't installed
     * or {@code initiator} isn't in a party. Looked up fresh here rather than once at
     * construction time - {@code DungeonModule} enables well before {@code ExtraApiModule}
     * (which publishes {@link PartyApi}) in this jar's single fixed module order, so a cached
     * lookup from the constructor would always see {@code null} even with party fully available.
     */
    private PartyResolution resolveParty(Player initiator) {
        PartyApi partyApi = services.load(PartyApi.class);
        if (partyApi != null) {
            Set<UUID> memberIds = partyApi.getMemberIds(initiator.getUniqueId());
            if (!memberIds.isEmpty()) {
                UUID leaderId = partyApi.getLeaderId(initiator.getUniqueId()).orElse(initiator.getUniqueId());
                List<Player> onlineMembers = memberIds.stream()
                        .map(Bukkit::getPlayer)
                        .filter(Objects::nonNull)
                        .toList();
                return new PartyResolution(leaderId, onlineMembers);
            }
        }
        return new PartyResolution(initiator.getUniqueId(), List.of(initiator));
    }

    private void spawnEncounter(DungeonInstance instance, Integer difficulty) {
        DungeonData data = instance.getData();
        DungeonArena arena = data.getArenas().get(instance.getArenaIndex());
        var world = Bukkit.getWorld(arena.world());
        if (world == null) {
            return;
        }
        Location entry = new Location(world, arena.x(), arena.y(), arena.z());
        for (Map.Entry<String, Integer> entry2 : data.getEnemies().entrySet()) {
            for (int i = 0; i < entry2.getValue(); i++) {
                combatApi.spawnMonster(entry2.getKey(), jitter(entry), difficulty).ifPresent(mob -> track(instance, mob));
            }
        }
        if (data.getBossId() != null) {
            combatApi.spawnBoss(data.getBossId(), jitter(entry), difficulty).ifPresent(boss -> track(instance, boss));
        }
    }

    private void track(DungeonInstance instance, LivingEntity entity) {
        instance.trackMonster(entity.getUniqueId());
        instanceManager.registerMonster(entity.getUniqueId(), instance.getId());
    }

    private Location jitter(Location center) {
        double dx = (random.nextDouble() * 2 - 1) * SPAWN_JITTER_RADIUS;
        double dz = (random.nextDouble() * 2 - 1) * SPAWN_JITTER_RADIUS;
        return center.clone().add(dx, 0, dz);
    }

    private void scheduleTimeout(DungeonInstance instance) {
        long delayTicks = instance.getData().getTimeLimitSeconds() * 20L;
        instance.setTimeoutTask(schedulerService.runLater(() -> forceEnd(instance, DungeonEndReason.TIMED_OUT), delayTicks));
    }

    private void forceEnd(DungeonInstance instance, DungeonEndReason reason) {
        instance.cancelTimeoutTask();
        despawnRemainingMobs(instance);
        Set<UUID> memberIds = instance.getMembers().keySet();
        String dungeonId = instance.getData().getId();
        UUID anyMember = memberIds.iterator().next();
        dungeonService.finish(anyMember, reason);
        if (reason == DungeonEndReason.CLEARED) {
            memberIds.forEach(id -> questProgressService.onDungeonCleared(id, dungeonId));
            grantRelicDrops(instance, memberIds);
        }
        notifyOutcome(instance, memberIds, reason);
    }

    /** Only bossed dungeons drop relics - see {@code rpg.api.RelicApi#generateRelic}, dungeons.yml's boss-id. */
    private void grantRelicDrops(DungeonInstance instance, Set<UUID> memberIds) {
        if (instance.getData().getBossId() == null) {
            return;
        }
        String dungeonId = instance.getData().getId();
        var config = configManager.get("config.yml").get();
        int min = config.getInt("dungeon.relic-drop-min", DEFAULT_RELIC_DROP_MIN);
        int max = Math.max(min, config.getInt("dungeon.relic-drop-max", DEFAULT_RELIC_DROP_MAX));
        for (UUID memberId : memberIds) {
            Player member = Bukkit.getPlayer(memberId);
            if (member == null) {
                continue;
            }
            int count = min + random.nextInt(max - min + 1);
            int granted = 0;
            for (int i = 0; i < count; i++) {
                if (relicApi.generateRelic(dungeonId).map(relic -> {
                    member.getInventory().addItem(relic).values()
                            .forEach(leftover -> member.getWorld().dropItemNaturally(member.getLocation(), leftover));
                    return true;
                }).orElse(false)) {
                    granted++;
                }
            }
            if (granted > 0) {
                messages.send(member, "dungeon.relic-reward", "count", granted);
            }
        }
    }

    private void despawnRemainingMobs(DungeonInstance instance) {
        for (UUID entityId : instance.getAliveMonsterIds()) {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity instanceof LivingEntity living && living.isValid()) {
                living.remove();
            }
        }
    }

    private void notifyOutcome(DungeonInstance instance, Set<UUID> memberIds, DungeonEndReason reason) {
        String key = switch (reason) {
            case CLEARED -> "dungeon.cleared";
            case TIMED_OUT -> "dungeon.timed-out";
            case RETIRED -> "dungeon.retired";
        };
        for (UUID memberId : memberIds) {
            Player member = Bukkit.getPlayer(memberId);
            if (member == null) {
                continue;
            }
            if (reason == DungeonEndReason.CLEARED) {
                // Reward amounts weren't shown anywhere before - clearing silently granted EXP/
                // money with only a generic "cleared!" message, and grantRelicDrops (above)
                // reports its own count separately since it varies per member.
                messages.send(member, key, "exp", instance.getData().getRewardExp(),
                        "money", MoneyFormat.format(instance.getData().getRewardMoney()));
            } else {
                messages.send(member, key);
            }
        }
    }

    private ChallengeFailure mapFailure(DungeonService.StartFailure failure) {
        return switch (failure) {
            case UNKNOWN_DUNGEON -> ChallengeFailure.UNKNOWN_DUNGEON;
            case PARTY_TOO_SMALL -> ChallengeFailure.PARTY_TOO_SMALL;
            case PARTY_TOO_LARGE -> ChallengeFailure.PARTY_TOO_LARGE;
            case WORLD_NOT_FOUND -> ChallengeFailure.WORLD_NOT_FOUND;
            case ALREADY_IN_DUNGEON -> ChallengeFailure.ALREADY_IN_DUNGEON;
            case DUNGEON_FULL -> ChallengeFailure.DUNGEON_FULL;
        };
    }
}
