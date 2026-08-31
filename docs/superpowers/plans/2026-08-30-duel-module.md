# DuelModule Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a `DuelModule` to orelia-core: 1v1 duel requests, multi-arena registration, HP-threshold duel resolution with no real death, a small server-funded money reward, and a simple win/loss leaderboard.

**Architecture:** New `rpg/extra/duel/` package following the established `repository/model/service/manager/listener/command/gui` layering every other `extra` module uses. A new `EventPriority.HIGH` listener runs *after* `rpg.monster.listener.CombatDamageListener`'s existing `EventPriority.LOW` handler (which already reduces a player's scaled `currentHp` and sets vanilla-equivalent damage for both melee and projectile PvP) to detect a lethal hit inside an active duel, cancel the underlying event before vanilla death applies, and resolve the duel.

**Tech Stack:** Java 21, Paper API (Bukkit events/commands/GUI inventories), JUnit 5, existing orelia-core frameworks (`Gui`/`GuiButton`/`GuiManager`, `PendingQueue<UUID>`, `SchemaOwner`/`DatabaseManager`, `ConfigManager`/`ConfigFile`, `MessageManager`, Vault `Economy`).

**Spec:** `docs/superpowers/specs/2026-08-30-duel-module-design.md`

## Global Constraints

- Money reward comes from the server (Vault `Economy.depositPlayer`), not a player-vs-player wager - default amount 50, configurable via `config.yml`'s `duel.reward-money`.
- Duel request cooldown default 60 seconds, configurable via `config.yml`'s `duel.cooldown-seconds`.
- No real player death, no item loss, no ELO rating, no spectating - out of scope per the spec.
- Every module reads/writes only through the layering above - no module reaching into another module's internals directly (matches `CLAUDE.md`'s cross-module convention).
- `duels.yml`/`config.yml`/`messages.yml` all need a `config-version` bump when a task adds a new key to them (bump once per file, the first time a task touches it - later tasks touching the same file do not bump it again).
- Build with `./gradlew build` after every task; all existing tests must stay green.

---

## File Structure

```
src/main/java/rpg/extra/duel/
  DuelModule.java
  model/
    DuelArena.java
    DuelSession.java
  repository/
    DuelArenaRepository.java       (duels.yml, config-driven - mirrors rpg.dungeon.repository.DungeonRepository's arena handling but flat, no parent dungeon-id)
    DuelStatsRepository.java       (DB, SchemaOwner - mirrors rpg.economy.repository.EconomyRepository/BankRepository)
  service/
    DuelArenaAdminService.java     (add/set/remove/list arenas - mirrors rpg.dungeon.service.DungeonArenaAdminService)
    DuelArenaAllocator.java        (pure: pick a free arena index given occupied indices)
    DuelStatsService.java          (wraps DuelStatsRepository: recordWin/recordLoss/topByWins)
    DuelService.java               (request/accept/decline/cancel/forfeit, orchestrates session start/end)
  manager/
    DuelRequestManager.java        (PendingQueue<UUID> wrapper - mirrors rpg.extra.friend.manager.FriendRequestManager)
    DuelSessionManager.java        (active DuelSession tracking by player uuid, arena claim/release)
  listener/
    DuelDamageListener.java        (EventPriority.HIGH on EntityDamageByEntityEvent)
    DuelQuitListener.java          (forfeit on quit)
  command/
    DuelCommand.java               (/ol duel request|accept|decline|cancel|forfeit|ranking|gui)
    DuelArenaAdminCommand.java     (/oladmin duelarena add|set|remove|list)
  gui/
    DuelGuiScreen.java             (pending requests list - mirrors rpg.extra.friend.gui.FriendGuiScreen)
    DuelRankingGuiScreen.java      (top-wins leaderboard - mirrors rpg.extra.ranking.gui.RankingGuiScreen)

src/test/java/rpg/extra/duel/
  service/
    DuelArenaAllocatorTest.java
    DuelStatsServiceTest.java

src/main/resources/
  duels.yml                        (new file)
  config.yml                       (add duel: section)
  messages.yml                     (add duel: section)
```

---

### Task 1: DuelArena model + pure arena-allocation logic

**Files:**
- Create: `src/main/java/rpg/extra/duel/model/DuelArena.java`
- Create: `src/main/java/rpg/extra/duel/service/DuelArenaAllocator.java`
- Test: `src/test/java/rpg/extra/duel/service/DuelArenaAllocatorTest.java`

**Interfaces:**
- Produces: `DuelArena` record `(String world, double x, double y, double z, float yaw, float pitch)`. `DuelArenaAllocator.findFreeIndex(int totalArenas, Set<Integer> occupiedIndices)` returning `Optional<Integer>` (0-based).

- [ ] **Step 1: Write the failing test**

```java
package rpg.extra.duel.service;

import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DuelArenaAllocatorTest {

    @Test
    void returnsFirstFreeIndexWhenNoneOccupied() {
        assertEquals(Optional.of(0), DuelArenaAllocator.findFreeIndex(3, Set.of()));
    }

    @Test
    void skipsOccupiedIndices() {
        assertEquals(Optional.of(1), DuelArenaAllocator.findFreeIndex(3, Set.of(0)));
        assertEquals(Optional.of(2), DuelArenaAllocator.findFreeIndex(3, Set.of(0, 1)));
    }

    @Test
    void emptyWhenAllArenasOccupied() {
        assertTrue(DuelArenaAllocator.findFreeIndex(2, Set.of(0, 1)).isEmpty());
    }

    @Test
    void emptyWhenNoArenasConfigured() {
        assertTrue(DuelArenaAllocator.findFreeIndex(0, Set.of()).isEmpty());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "rpg.extra.duel.service.DuelArenaAllocatorTest"`
Expected: FAIL (compile error - `DuelArenaAllocator` doesn't exist yet)

- [ ] **Step 3: Write the model and the minimal implementation**

```java
package rpg.extra.duel.model;

/** One physical location a duel can be spawned at (mirrors rpg.dungeon.model.DungeonArena, but flat - no parent dungeon-id, since a duel isn't tied to any other content entity). */
public record DuelArena(String world, double x, double y, double z, float yaw, float pitch) {
}
```

```java
package rpg.extra.duel.service;

import java.util.Optional;
import java.util.Set;

/**
 * Pure index-selection logic for picking a free duel arena - kept Bukkit-independent (works on
 * plain ints/indices, not Location/DuelArena objects) so it's directly unit-testable, same
 * reasoning rpg.region.service.RegionQueryService#orderByEffectivePriority is pulled out pure.
 */
public final class DuelArenaAllocator {

    private DuelArenaAllocator() {
    }

    /** First 0-based index in [0, totalArenas) not present in occupiedIndices, empty if every arena is occupied (or none exist). */
    public static Optional<Integer> findFreeIndex(int totalArenas, Set<Integer> occupiedIndices) {
        for (int i = 0; i < totalArenas; i++) {
            if (!occupiedIndices.contains(i)) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "rpg.extra.duel.service.DuelArenaAllocatorTest"`
Expected: PASS (4 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/rpg/extra/duel/model/DuelArena.java \
        src/main/java/rpg/extra/duel/service/DuelArenaAllocator.java \
        src/test/java/rpg/extra/duel/service/DuelArenaAllocatorTest.java
git commit -m "Add DuelArena model and pure arena-allocation logic"
```

---

### Task 2: DuelArenaRepository (duels.yml persistence)

**Files:**
- Create: `src/main/java/rpg/extra/duel/repository/DuelArenaRepository.java`
- Create: `src/main/resources/duels.yml`

**Interfaces:**
- Consumes: `DuelArena` (Task 1).
- Produces: `DuelArenaRepository` with `void load(YamlConfiguration config)`, `List<DuelArena> getAll()`, `void replace(List<DuelArena> arenas)`, `void save(ConfigFile file)`.

- [ ] **Step 1: Create the config file**

```yaml
# duels.yml
# arenas: flat, 0-indexed list of physical duel locations, managed via
# /oladmin duelarena add|set|remove|list - do not hand-edit unless you know what you're doing.
config-version: 1

arenas: {}
```

- [ ] **Step 2: Write DuelArenaRepository**

```java
package rpg.extra.duel.repository;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.core.config.ConfigFile;
import rpg.extra.duel.model.DuelArena;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Config-driven (duels.yml) storage for the flat arena list - mirrors
 * rpg.dungeon.repository.DungeonRepository's own arenas: parsing, but with no dungeon-id parent
 * key since a duel arena isn't owned by any other content entity.
 */
public final class DuelArenaRepository {

    private List<DuelArena> arenas = new ArrayList<>();

    public void load(YamlConfiguration config) {
        List<DuelArena> loaded = new ArrayList<>();
        ConfigurationSection section = config.getConfigurationSection("arenas");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(key);
                if (entry == null) {
                    continue;
                }
                loaded.add(new DuelArena(
                        entry.getString("world", "world"),
                        entry.getDouble("x"),
                        entry.getDouble("y"),
                        entry.getDouble("z"),
                        (float) entry.getDouble("yaw", 0.0),
                        (float) entry.getDouble("pitch", 0.0)));
            }
        }
        this.arenas = loaded;
    }

    public List<DuelArena> getAll() {
        return List.copyOf(arenas);
    }

    /** Replaces the in-memory arena list only - call {@link #save} separately to persist to disk. */
    public void replace(List<DuelArena> updated) {
        this.arenas = new ArrayList<>(updated);
    }

    /** Writes the current in-memory arena list back to {@code file}, keyed by 0-based index. */
    public void save(ConfigFile file) {
        YamlConfiguration config = file.get();
        Map<String, Object> raw = new LinkedHashMap<>();
        for (int i = 0; i < arenas.size(); i++) {
            DuelArena arena = arenas.get(i);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("world", arena.world());
            entry.put("x", arena.x());
            entry.put("y", arena.y());
            entry.put("z", arena.z());
            entry.put("yaw", arena.yaw());
            entry.put("pitch", arena.pitch());
            raw.put(String.valueOf(i), entry);
        }
        config.set("arenas", raw);
        file.save();
    }
}
```

- [ ] **Step 3: Build to confirm it compiles (no test yet - this class is exercised end-to-end by Task 3's admin service, which is where the testable behavior lives)**

Run: `./gradlew build -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/rpg/extra/duel/repository/DuelArenaRepository.java src/main/resources/duels.yml
git commit -m "Add DuelArenaRepository (duels.yml persistence)"
```

---

### Task 3: DuelArenaAdminService + DuelArenaAdminCommand

**Files:**
- Create: `src/main/java/rpg/extra/duel/service/DuelArenaAdminService.java`
- Create: `src/main/java/rpg/extra/duel/command/DuelArenaAdminCommand.java`

**Interfaces:**
- Consumes: `DuelArenaRepository` (Task 2), `DuelArena` (Task 1).
- Produces: `DuelArenaAdminService` with `DuelArena addArena(Location)`, `enum SetResult{OK,INDEX_OUT_OF_RANGE}` + `SetResult setArena(int index, Location)`, `enum RemoveResult{OK,INDEX_OUT_OF_RANGE}` + `RemoveResult removeArena(int index)`, `List<DuelArena> listArenas()`.

- [ ] **Step 1: Write DuelArenaAdminService**

```java
package rpg.extra.duel.service;

import org.bukkit.Location;
import rpg.core.config.ConfigFile;
import rpg.core.config.ConfigManager;
import rpg.extra.duel.model.DuelArena;
import rpg.extra.duel.repository.DuelArenaRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Backs /oladmin duelarena add|set|remove|list - mirrors rpg.dungeon.service.DungeonArenaAdminService
 * but flat (no dungeon-id parent, no "last arena" restriction - an empty arena list is a valid
 * "not configured yet" state for duels, unlike dungeons' legacy-scalar-fallback concern).
 */
public final class DuelArenaAdminService {

    private static final String DUELS_YML = "duels.yml";

    private final DuelArenaRepository repository;
    private final ConfigManager configManager;

    public DuelArenaAdminService(DuelArenaRepository repository, ConfigManager configManager) {
        this.repository = repository;
        this.configManager = configManager;
    }

    public DuelArena addArena(Location location) {
        DuelArena arena = fromLocation(location);
        List<DuelArena> arenas = new ArrayList<>(repository.getAll());
        arenas.add(arena);
        apply(arenas);
        return arena;
    }

    public enum SetResult { OK, INDEX_OUT_OF_RANGE }

    /** 1-based index, matching {@link #listArenas}'s numbering (same convention DungeonArenaAdminService uses). */
    public SetResult setArena(int index, Location location) {
        List<DuelArena> arenas = new ArrayList<>(repository.getAll());
        if (index < 1 || index > arenas.size()) {
            return SetResult.INDEX_OUT_OF_RANGE;
        }
        arenas.set(index - 1, fromLocation(location));
        apply(arenas);
        return SetResult.OK;
    }

    public enum RemoveResult { OK, INDEX_OUT_OF_RANGE }

    public RemoveResult removeArena(int index) {
        List<DuelArena> arenas = new ArrayList<>(repository.getAll());
        if (index < 1 || index > arenas.size()) {
            return RemoveResult.INDEX_OUT_OF_RANGE;
        }
        arenas.remove(index - 1);
        apply(arenas);
        return RemoveResult.OK;
    }

    public List<DuelArena> listArenas() {
        return repository.getAll();
    }

    private DuelArena fromLocation(Location location) {
        return new DuelArena(location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch());
    }

    private void apply(List<DuelArena> arenas) {
        repository.replace(arenas);
        ConfigFile file = configManager.get(DUELS_YML);
        repository.save(file);
    }
}
```

- [ ] **Step 2: Write DuelArenaAdminCommand**

```java
package rpg.extra.duel.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import rpg.core.command.TabCompletions;
import rpg.core.message.MessageManager;
import rpg.extra.duel.model.DuelArena;
import rpg.extra.duel.service.DuelArenaAdminService;

import java.util.List;

/**
 * {@code /oladmin duelarena add|set <index>|remove <index>|list} - mirrors
 * rpg.dungeon.command.DungeonArenaAdminCommand but with no dungeon-id argument, since duel
 * arenas are a single flat list.
 */
public final class DuelArenaAdminCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("add", "set", "remove", "list");

    private final DuelArenaAdminService adminService;
    private final MessageManager messages;

    public DuelArenaAdminCommand(DuelArenaAdminService adminService, MessageManager messages) {
        this.adminService = adminService;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "duel.admin.arena-usage");
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "add" -> add(sender);
            case "set" -> set(sender, args);
            case "remove" -> remove(sender, args);
            case "list" -> list(sender);
            default -> messages.send(sender, "duel.admin.arena-usage");
        }
        return true;
    }

    private void add(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return;
        }
        adminService.addArena(player.getLocation());
        messages.send(sender, "duel.admin.arena-added");
    }

    private void set(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return;
        }
        if (args.length < 2) {
            messages.send(sender, "duel.admin.arena-usage");
            return;
        }
        int index;
        try {
            index = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            messages.send(sender, "duel.admin.arena-usage");
            return;
        }
        DuelArenaAdminService.SetResult result = adminService.setArena(index, player.getLocation());
        switch (result) {
            case OK -> messages.send(sender, "duel.admin.arena-set", "index", index);
            case INDEX_OUT_OF_RANGE -> messages.send(sender, "duel.admin.arena-index-out-of-range");
        }
    }

    private void remove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messages.send(sender, "duel.admin.arena-usage");
            return;
        }
        int index;
        try {
            index = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            messages.send(sender, "duel.admin.arena-usage");
            return;
        }
        DuelArenaAdminService.RemoveResult result = adminService.removeArena(index);
        switch (result) {
            case OK -> messages.send(sender, "duel.admin.arena-removed", "index", index);
            case INDEX_OUT_OF_RANGE -> messages.send(sender, "duel.admin.arena-index-out-of-range");
        }
    }

    private void list(CommandSender sender) {
        List<DuelArena> arenas = adminService.listArenas();
        messages.send(sender, "duel.admin.arena-list-header");
        int index = 1;
        for (DuelArena arena : arenas) {
            messages.sendRaw(sender, "duel.admin.arena-list-entry",
                    "index", index, "world", arena.world(), "x", (int) arena.x(), "y", (int) arena.y(), "z", (int) arena.z());
            index++;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            return TabCompletions.matching(SUBCOMMANDS, args.length == 0 ? "" : args[0]);
        }
        return List.of();
    }
}
```

- [ ] **Step 3: Build to confirm it compiles**

Run: `./gradlew build -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/rpg/extra/duel/service/DuelArenaAdminService.java \
        src/main/java/rpg/extra/duel/command/DuelArenaAdminCommand.java
git commit -m "Add DuelArenaAdminService and /oladmin duelarena command"
```

---

### Task 4: DuelStatsRepository (DB persistence)

**Files:**
- Create: `src/main/java/rpg/extra/duel/repository/DuelStatsRepository.java`

**Interfaces:**
- Produces: `DuelStatsRepository implements SchemaOwner` with `void createSchemaIfNotExists()`, `void recordWin(UUID)`, `void recordLoss(UUID)`, `record DuelStatsEntry(UUID uuid, int wins, int losses) {}`, `List<DuelStatsEntry> topByWins(int limit)`.

- [ ] **Step 1: Write DuelStatsRepository**

```java
package rpg.extra.duel.repository;

import rpg.database.manager.DatabaseManager;
import rpg.database.repository.SchemaOwner;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DB-backed win/loss counters, one row per player who has ever finished a duel - mirrors
 * rpg.economy.repository.EconomyRepository/BankRepository's SchemaOwner-on-shared-DatabaseManager
 * convention.
 */
public final class DuelStatsRepository implements SchemaOwner {

    public record DuelStatsEntry(UUID uuid, int wins, int losses) {
    }

    private final DatabaseManager databaseManager;

    public DuelStatsRepository(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    @Override
    public void createSchemaIfNotExists() throws SQLException {
        try (Connection connection = databaseManager.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE IF NOT EXISTS duel_stats (
                        uuid VARCHAR(36) PRIMARY KEY,
                        wins INTEGER NOT NULL DEFAULT 0,
                        losses INTEGER NOT NULL DEFAULT 0
                    )
                    """);
        }
    }

    public void recordWin(UUID uuid) {
        upsert(uuid, 1, 0);
    }

    public void recordLoss(UUID uuid) {
        upsert(uuid, 0, 1);
    }

    private void upsert(UUID uuid, int winsDelta, int lossesDelta) {
        String sql = switch (databaseManager.getType()) {
            case SQLITE -> """
                    INSERT INTO duel_stats (uuid, wins, losses) VALUES (?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET wins = wins + excluded.wins, losses = losses + excluded.losses
                    """;
            case MYSQL -> """
                    INSERT INTO duel_stats (uuid, wins, losses) VALUES (?, ?, ?)
                    ON DUPLICATE KEY UPDATE wins = wins + VALUES(wins), losses = losses + VALUES(losses)
                    """;
        };
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, winsDelta);
            statement.setInt(3, lossesDelta);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update duel stats for " + uuid, e);
        }
    }

    public List<DuelStatsEntry> topByWins(int limit) {
        String sql = "SELECT uuid, wins, losses FROM duel_stats ORDER BY wins DESC LIMIT ?";
        List<DuelStatsEntry> result = new ArrayList<>();
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, limit);
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(new DuelStatsEntry(UUID.fromString(resultSet.getString("uuid")),
                            resultSet.getInt("wins"), resultSet.getInt("losses")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read duel stats leaderboard", e);
        }
        return result;
    }
}
```

- [ ] **Step 2: Build to confirm it compiles (DB-connection-dependent, no unit test - matches EconomyRepository/BankRepository's own untested precedent for the same reason)**

Run: `./gradlew build -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/rpg/extra/duel/repository/DuelStatsRepository.java
git commit -m "Add DuelStatsRepository (duel_stats DB table)"
```

---

### Task 5: DuelStatsService + pure ranking-sort test

**Files:**
- Create: `src/main/java/rpg/extra/duel/service/DuelStatsService.java`
- Test: `src/test/java/rpg/extra/duel/service/DuelStatsServiceTest.java`

**Interfaces:**
- Consumes: `DuelStatsRepository`, `DuelStatsRepository.DuelStatsEntry` (Task 4).
- Produces: `DuelStatsService` with `void recordResult(UUID winner, UUID loser)`, `List<DuelStatsRepository.DuelStatsEntry> topByWins(int limit)`, and the pure helper `static List<DuelStatsRepository.DuelStatsEntry> sortByWinsDescending(List<DuelStatsRepository.DuelStatsEntry> entries)` used internally (exercised directly by the test, independent of any DB).

- [ ] **Step 1: Write the failing test**

```java
package rpg.extra.duel.service;

import org.junit.jupiter.api.Test;
import rpg.extra.duel.repository.DuelStatsRepository.DuelStatsEntry;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DuelStatsServiceTest {

    @Test
    void sortsByWinsDescending() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        List<DuelStatsEntry> input = List.of(
                new DuelStatsEntry(a, 3, 5),
                new DuelStatsEntry(b, 10, 1),
                new DuelStatsEntry(c, 7, 2));

        List<DuelStatsEntry> sorted = DuelStatsService.sortByWinsDescending(input);

        assertEquals(List.of(b, c, a), sorted.stream().map(DuelStatsEntry::uuid).toList());
    }

    @Test
    void stableForEqualWins() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        List<DuelStatsEntry> input = List.of(
                new DuelStatsEntry(a, 5, 0),
                new DuelStatsEntry(b, 5, 0));

        List<DuelStatsEntry> sorted = DuelStatsService.sortByWinsDescending(input);

        assertEquals(List.of(a, b), sorted.stream().map(DuelStatsEntry::uuid).toList());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "rpg.extra.duel.service.DuelStatsServiceTest"`
Expected: FAIL (compile error - `DuelStatsService` doesn't exist yet)

- [ ] **Step 3: Write the minimal implementation**

```java
package rpg.extra.duel.service;

import rpg.extra.duel.repository.DuelStatsRepository;
import rpg.extra.duel.repository.DuelStatsRepository.DuelStatsEntry;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Business logic over {@link DuelStatsRepository} - records a finished duel's result and serves the leaderboard. */
public final class DuelStatsService {

    private final DuelStatsRepository repository;

    public DuelStatsService(DuelStatsRepository repository) {
        this.repository = repository;
    }

    public void recordResult(UUID winner, UUID loser) {
        repository.recordWin(winner);
        repository.recordLoss(loser);
    }

    public List<DuelStatsEntry> topByWins(int limit) {
        return sortByWinsDescending(repository.topByWins(limit));
    }

    /** Pure, DB-independent - {@code repository.topByWins} already orders by SQL, but this is the single place that decision lives so it's directly testable without a database. */
    static List<DuelStatsEntry> sortByWinsDescending(List<DuelStatsEntry> entries) {
        return entries.stream()
                .sorted(Comparator.comparingInt(DuelStatsEntry::wins).reversed())
                .toList();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "rpg.extra.duel.service.DuelStatsServiceTest"`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add src/main/java/rpg/extra/duel/service/DuelStatsService.java \
        src/test/java/rpg/extra/duel/service/DuelStatsServiceTest.java
git commit -m "Add DuelStatsService with a pure, tested ranking sort"
```

---

### Task 6: DuelSession model + DuelSessionManager

**Files:**
- Create: `src/main/java/rpg/extra/duel/model/DuelSession.java`
- Create: `src/main/java/rpg/extra/duel/manager/DuelSessionManager.java`

**Interfaces:**
- Consumes: `DuelArena`/`DuelArenaAllocator` (Task 1), `DuelArenaRepository` (Task 2).
- Produces: `DuelSession` (mutable holder for both players' pre-duel locations + arena index). `DuelSessionManager` with `Optional<DuelSession> start(Player a, Player b)`, `Optional<DuelSession> sessionOf(UUID playerId)`, `void end(DuelSession session)`.

- [ ] **Step 1: Write DuelSession**

```java
package rpg.extra.duel.model;

import org.bukkit.Location;

import java.util.UUID;

/**
 * One in-progress duel between two players - mutable, in-memory only (not persisted; a duel is
 * a short-lived interaction, same reasoning rpg.extra.party.model.Party has no DB backing
 * either - it's rebuilt fresh from PlayerData/quit events, there's nothing meaningful to
 * restore across a server restart).
 */
public final class DuelSession {

    private final UUID playerA;
    private final UUID playerB;
    private final Location returnLocationA;
    private final Location returnLocationB;
    private final int arenaIndex;

    public DuelSession(UUID playerA, UUID playerB, Location returnLocationA, Location returnLocationB, int arenaIndex) {
        this.playerA = playerA;
        this.playerB = playerB;
        this.returnLocationA = returnLocationA;
        this.returnLocationB = returnLocationB;
        this.arenaIndex = arenaIndex;
    }

    public UUID getPlayerA() {
        return playerA;
    }

    public UUID getPlayerB() {
        return playerB;
    }

    public Location getReturnLocation(UUID playerId) {
        return playerId.equals(playerA) ? returnLocationA : returnLocationB;
    }

    /** The other participant's id - throws if {@code playerId} isn't part of this session, which would be a caller bug. */
    public UUID opponentOf(UUID playerId) {
        if (playerId.equals(playerA)) {
            return playerB;
        }
        if (playerId.equals(playerB)) {
            return playerA;
        }
        throw new IllegalArgumentException(playerId + " is not part of this duel session");
    }

    public boolean involves(UUID playerId) {
        return playerId.equals(playerA) || playerId.equals(playerB);
    }

    public int getArenaIndex() {
        return arenaIndex;
    }
}
```

- [ ] **Step 2: Write DuelSessionManager**

```java
package rpg.extra.duel.manager;

import org.bukkit.entity.Player;
import rpg.extra.duel.model.DuelArena;
import rpg.extra.duel.model.DuelSession;
import rpg.extra.duel.repository.DuelArenaRepository;
import rpg.extra.duel.service.DuelArenaAllocator;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks every currently-active {@link DuelSession}, keyed by both participants' UUIDs (so a
 * lookup from either side is O(1)), and which arena indices are currently occupied.
 */
public final class DuelSessionManager {

    private final DuelArenaRepository arenaRepository;
    private final Map<UUID, DuelSession> sessionsByPlayer = new ConcurrentHashMap<>();
    private final Set<Integer> occupiedArenaIndices = ConcurrentHashMap.newKeySet();

    public DuelSessionManager(DuelArenaRepository arenaRepository) {
        this.arenaRepository = arenaRepository;
    }

    /** Empty if no arena is currently free - caller is responsible for messaging the two players. */
    public Optional<DuelSession> start(Player a, Player b) {
        java.util.List<DuelArena> arenas = arenaRepository.getAll();
        Optional<Integer> freeIndex = DuelArenaAllocator.findFreeIndex(arenas.size(), Set.copyOf(occupiedArenaIndices));
        if (freeIndex.isEmpty()) {
            return Optional.empty();
        }
        int index = freeIndex.get();
        DuelArena arena = arenas.get(index);
        DuelSession session = new DuelSession(a.getUniqueId(), b.getUniqueId(),
                a.getLocation().clone(), b.getLocation().clone(), index);
        occupiedArenaIndices.add(index);
        sessionsByPlayer.put(a.getUniqueId(), session);
        sessionsByPlayer.put(b.getUniqueId(), session);
        org.bukkit.Location destination = new org.bukkit.Location(
                org.bukkit.Bukkit.getWorld(arena.world()), arena.x(), arena.y(), arena.z(), arena.yaw(), arena.pitch());
        a.teleport(destination);
        b.teleport(destination);
        return Optional.of(session);
    }

    public Optional<DuelSession> sessionOf(UUID playerId) {
        return Optional.ofNullable(sessionsByPlayer.get(playerId));
    }

    /** Removes {@code session} from tracking and frees its arena - does not teleport/heal anyone, callers (DuelService/DuelDamageListener) do that once, after deciding the outcome. */
    public void end(DuelSession session) {
        sessionsByPlayer.remove(session.getPlayerA());
        sessionsByPlayer.remove(session.getPlayerB());
        occupiedArenaIndices.remove(session.getArenaIndex());
    }
}
```

- [ ] **Step 3: Build to confirm it compiles**

Run: `./gradlew build -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/rpg/extra/duel/model/DuelSession.java \
        src/main/java/rpg/extra/duel/manager/DuelSessionManager.java
git commit -m "Add DuelSession model and DuelSessionManager"
```

---

### Task 7: DuelRequestManager + DuelService

**Files:**
- Create: `src/main/java/rpg/extra/duel/manager/DuelRequestManager.java`
- Create: `src/main/java/rpg/extra/duel/service/DuelService.java`

**Interfaces:**
- Consumes: `PendingQueue<UUID>` (`rpg.core.util.PendingQueue`, existing), `DuelSessionManager` (Task 6), `DuelStatsService` (Task 5).
- Produces: `DuelRequestManager` (same shape as `FriendRequestManager`). `DuelService` with `enum RequestResult{OK,ALREADY_PENDING,ON_COOLDOWN,SELF}`, `RequestResult request(Player requester, Player target)`, `enum AcceptResult{OK,NO_ARENA_FREE,NO_PENDING_REQUEST}`, `AcceptResult accept(Player target, UUID requesterId)`, `boolean decline(Player target, UUID requesterId)`, `boolean cancel(Player requester, UUID targetId)`, `void forfeit(Player player)`.

- [ ] **Step 1: Write DuelRequestManager**

```java
package rpg.extra.duel.manager;

import rpg.core.util.PendingQueue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Tracks pending (unanswered) duel requests - mirrors rpg.extra.friend.manager.FriendRequestManager exactly, same PendingQueue-per-target shape. */
public final class DuelRequestManager {

    private final PendingQueue<UUID> pendingRequests = new PendingQueue<>();

    public void request(UUID requesterId, UUID targetId) {
        pendingRequests.add(targetId, requesterId);
    }

    public Optional<UUID> peekOldest(UUID targetId) {
        return pendingRequests.peekOldest(targetId);
    }

    public List<UUID> peekAll(UUID targetId) {
        return pendingRequests.peekAll(targetId);
    }

    public boolean hasPendingFrom(UUID targetId, UUID requesterId) {
        return pendingRequests.peekAll(targetId).contains(requesterId);
    }

    public Optional<UUID> consume(UUID targetId) {
        return pendingRequests.consumeOldest(targetId);
    }

    public Optional<UUID> consume(UUID targetId, UUID requesterId) {
        return pendingRequests.consume(targetId, requesterId);
    }

    public void clear(UUID targetId) {
        pendingRequests.clear(targetId);
    }
}
```

- [ ] **Step 2: Write DuelService**

```java
package rpg.extra.duel.service;

import org.bukkit.entity.Player;
import rpg.extra.duel.manager.DuelRequestManager;
import rpg.extra.duel.manager.DuelSessionManager;
import rpg.extra.duel.model.DuelSession;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Orchestrates the request -> accept/decline/cancel -> session-start flow, plus mid-duel forfeit. */
public final class DuelService {

    private final DuelRequestManager requestManager;
    private final DuelSessionManager sessionManager;
    private final long cooldownMillis;
    private final Map<UUID, Long> lastRequestAtMillis = new ConcurrentHashMap<>();

    public DuelService(DuelRequestManager requestManager, DuelSessionManager sessionManager, long cooldownSeconds) {
        this.requestManager = requestManager;
        this.sessionManager = sessionManager;
        this.cooldownMillis = cooldownSeconds * 1000L;
    }

    public enum RequestResult { OK, ALREADY_PENDING, ON_COOLDOWN, SELF }

    public RequestResult request(Player requester, Player target) {
        if (requester.getUniqueId().equals(target.getUniqueId())) {
            return RequestResult.SELF;
        }
        long last = lastRequestAtMillis.getOrDefault(requester.getUniqueId(), 0L);
        if (System.currentTimeMillis() - last < cooldownMillis) {
            return RequestResult.ON_COOLDOWN;
        }
        if (requestManager.hasPendingFrom(target.getUniqueId(), requester.getUniqueId())) {
            return RequestResult.ALREADY_PENDING;
        }
        requestManager.request(requester.getUniqueId(), target.getUniqueId());
        lastRequestAtMillis.put(requester.getUniqueId(), System.currentTimeMillis());
        return RequestResult.OK;
    }

    public enum AcceptResult { OK, NO_ARENA_FREE, NO_PENDING_REQUEST }

    /** {@code requesterId} - null accepts the oldest pending request (no-argument "/duel accept"), non-null accepts that specific one. */
    public AcceptResult accept(Player target, UUID requesterId, java.util.function.Function<UUID, Optional<Player>> onlinePlayerLookup) {
        Optional<UUID> consumed = requesterId == null
                ? requestManager.consume(target.getUniqueId())
                : requestManager.consume(target.getUniqueId(), requesterId);
        if (consumed.isEmpty()) {
            return AcceptResult.NO_PENDING_REQUEST;
        }
        Optional<Player> requester = onlinePlayerLookup.apply(consumed.get());
        if (requester.isEmpty()) {
            return AcceptResult.NO_PENDING_REQUEST;
        }
        Optional<DuelSession> session = sessionManager.start(requester.get(), target);
        return session.isPresent() ? AcceptResult.OK : AcceptResult.NO_ARENA_FREE;
    }

    public boolean decline(Player target, UUID requesterId) {
        Optional<UUID> consumed = requesterId == null
                ? requestManager.consume(target.getUniqueId())
                : requestManager.consume(target.getUniqueId(), requesterId);
        return consumed.isPresent();
    }

    public boolean cancel(Player requester, UUID targetId) {
        return requestManager.consume(targetId, requester.getUniqueId()).isPresent();
    }

    /** Clears any pending requests a departing/departed player was involved in - callers still handle the active-session forfeit path separately (DuelDamageListener/DuelQuitListener own that, since it needs teleport/heal/reward logic this class doesn't have). */
    public void clearPendingRequestsFor(UUID playerId) {
        requestManager.clear(playerId);
    }
}
```

- [ ] **Step 3: Build to confirm it compiles**

Run: `./gradlew build -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/rpg/extra/duel/manager/DuelRequestManager.java \
        src/main/java/rpg/extra/duel/service/DuelService.java
git commit -m "Add DuelRequestManager and DuelService (request/accept/decline/cancel)"
```

---

### Task 8: DuelDamageListener (the death-prevention hook)

**Files:**
- Create: `src/main/java/rpg/extra/duel/listener/DuelDamageListener.java`

**Interfaces:**
- Consumes: `DuelSessionManager` (Task 6), `DuelStatsService` (Task 5), `StatusApi` (existing, `rpg.api.StatusApi`), Vault `Economy` (existing).
- Produces: `DuelDamageListener implements Listener`, one public method `void resolveDuel(DuelSession session, UUID winnerId)` (also callable directly by `DuelQuitListener` in Task 9 for the forfeit path).

- [ ] **Step 1: Write DuelDamageListener**

```java
package rpg.extra.duel.listener;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import rpg.api.StatusApi;
import rpg.core.message.MessageManager;
import rpg.extra.duel.manager.DuelSessionManager;
import rpg.extra.duel.model.DuelSession;
import rpg.extra.duel.service.DuelStatsService;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Runs at {@link EventPriority#HIGH}, after {@code rpg.monster.listener.CombatDamageListener}'s
 * own {@code EventPriority.LOW} handler has already resolved final damage and reduced the
 * victim's scaled {@code currentHp} (see that class's {@code resolveFinalDamage} - this happens
 * synchronously, so by the time this handler runs the scaled HP drop has already landed). If the
 * victim is now at lethal scaled HP <b>and</b> both participants are in the same active duel,
 * cancels the event (preventing vanilla death/knockback) and resolves the duel instead of
 * letting it kill the loser for real.
 */
public final class DuelDamageListener implements Listener {

    private final DuelSessionManager sessionManager;
    private final DuelStatsService statsService;
    private final StatusApi statusApi;
    private final Economy economy;
    private final MessageManager messages;
    private final double rewardMoney;

    public DuelDamageListener(DuelSessionManager sessionManager, DuelStatsService statsService, StatusApi statusApi,
                               Economy economy, MessageManager messages, double rewardMoney) {
        this.sessionManager = sessionManager;
        this.statsService = statsService;
        this.statusApi = statusApi;
        this.economy = economy;
        this.messages = messages;
        this.rewardMoney = rewardMoney;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolveAttacker(event);
        if (attacker == null) {
            return;
        }
        Optional<DuelSession> maybeSession = sessionManager.sessionOf(victim.getUniqueId());
        if (maybeSession.isEmpty()) {
            return;
        }
        DuelSession session = maybeSession.get();
        if (!session.involves(attacker.getUniqueId())) {
            return; // third party hit a duelist - not a duel-ending blow
        }
        double currentHp = statusApi.getCurrentHp(victim.getUniqueId()).orElse(0.0);
        if (currentHp > 0) {
            return;
        }
        event.setCancelled(true);
        resolveDuel(session, attacker.getUniqueId());
    }

    private Player resolveAttacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    /** Ends {@code session}, declaring {@code winnerId} the winner - teleports both back, heals both to full, pays the reward, records stats, announces the result, and frees the arena. Also called directly by {@code DuelQuitListener} for the forfeit-on-quit path. */
    public void resolveDuel(DuelSession session, UUID winnerId) {
        UUID loserId = session.opponentOf(winnerId);
        teleportBackAndHeal(session, session.getPlayerA());
        teleportBackAndHeal(session, session.getPlayerB());
        economy.depositPlayer(Bukkit.getOfflinePlayer(winnerId), rewardMoney);
        statsService.recordResult(winnerId, loserId);
        sessionManager.end(session);
        Player winner = Bukkit.getPlayer(winnerId);
        Player loser = Bukkit.getPlayer(loserId);
        if (winner != null) {
            messages.sendWithSound(winner, "duel.won", null, "opponent", loser != null ? loser.getName() : loserId.toString(), "reward", rewardMoney);
        }
        if (loser != null) {
            messages.send(loser, "duel.lost", "opponent", winner != null ? winner.getName() : winnerId.toString());
        }
    }

    private void teleportBackAndHeal(DuelSession session, UUID playerId) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null) {
            return;
        }
        player.teleport(session.getReturnLocation(playerId));
        Map<String, Double> stats = statusApi.getFinalStats(playerId);
        double maxHp = stats.getOrDefault("HP", 0.0);
        double currentHp = statusApi.getCurrentHp(playerId).orElse(0.0);
        double missing = maxHp - currentHp;
        if (missing > 0) {
            statusApi.heal(playerId, missing);
        }
    }
}
```

- [ ] **Step 2: Build to confirm it compiles (Bukkit-event/entity-dependent, no unit test - matches CombatDamageListener's own untested precedent)**

Run: `./gradlew build -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/rpg/extra/duel/listener/DuelDamageListener.java
git commit -m "Add DuelDamageListener (HP-lethal detection, death cancel, duel resolution)"
```

---

### Task 9: DuelQuitListener (forfeit on disconnect)

**Files:**
- Create: `src/main/java/rpg/extra/duel/listener/DuelQuitListener.java`

**Interfaces:**
- Consumes: `DuelSessionManager` (Task 6), `DuelRequestManager` (Task 7), `DuelDamageListener.resolveDuel` (Task 8).

- [ ] **Step 1: Write DuelQuitListener**

```java
package rpg.extra.duel.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import rpg.extra.duel.manager.DuelRequestManager;
import rpg.extra.duel.manager.DuelSessionManager;
import rpg.extra.duel.model.DuelSession;

/** A player quitting mid-duel forfeits it (the remaining player is declared the winner); a player with pending duel requests just has them cleared, same as friend/party/guild's own quit listeners. */
public final class DuelQuitListener implements Listener {

    private final DuelSessionManager sessionManager;
    private final DuelRequestManager requestManager;
    private final DuelDamageListener damageListener;

    public DuelQuitListener(DuelSessionManager sessionManager, DuelRequestManager requestManager, DuelDamageListener damageListener) {
        this.sessionManager = sessionManager;
        this.requestManager = requestManager;
        this.damageListener = damageListener;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        var playerId = event.getPlayer().getUniqueId();
        requestManager.clear(playerId);
        sessionManager.sessionOf(playerId).ifPresent(session -> {
            var opponentId = session.opponentOf(playerId);
            damageListener.resolveDuel(session, opponentId);
        });
    }
}
```

- [ ] **Step 2: Build to confirm it compiles**

Run: `./gradlew build -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/main/java/rpg/extra/duel/listener/DuelQuitListener.java
git commit -m "Add DuelQuitListener (forfeit on disconnect)"
```

---

### Task 10: DuelGuiScreen + DuelCommand

**Files:**
- Create: `src/main/java/rpg/extra/duel/gui/DuelGuiScreen.java`
- Create: `src/main/java/rpg/extra/duel/command/DuelCommand.java`

**Interfaces:**
- Consumes: `DuelService`, `DuelRequestManager` (Task 7), `GuiManager`/`Gui`/`GuiButton`/`ItemBuilder` (existing).
- Produces: `DuelGuiScreen.build(Player)`, `DuelCommand implements CommandExecutor, TabCompleter`.

- [ ] **Step 1: Write DuelGuiScreen**

```java
package rpg.extra.duel.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
import rpg.extra.duel.manager.DuelRequestManager;
import rpg.extra.duel.service.DuelService;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.gui.framework.GuiPlayerHead;

import java.util.List;
import java.util.UUID;

/** Every player currently requesting a duel with the viewer, oldest first - mirrors rpg.extra.friend.gui.FriendGuiScreen's own pending-request list shape. */
public final class DuelGuiScreen {

    private final DuelRequestManager requestManager;
    private final DuelService duelService;
    private final MessageManager messages;

    public DuelGuiScreen(DuelRequestManager requestManager, DuelService duelService, MessageManager messages) {
        this.requestManager = requestManager;
        this.duelService = duelService;
        this.messages = messages;
    }

    public Gui build(Player player) {
        Gui gui = new Gui("&%8届いている決闘申請", 27);
        List<UUID> requesters = requestManager.peekAll(player.getUniqueId());
        if (requesters.isEmpty()) {
            gui.set(13, GuiButton.display(new rpg.util.ItemBuilder(Material.PAPER).name("&%7決闘申請はありません").build()));
            return gui;
        }
        int slot = 0;
        for (UUID requesterId : requesters) {
            if (slot >= 27) {
                break;
            }
            gui.set(slot++, requestButton(player, requesterId));
        }
        return gui;
    }

    private GuiButton requestButton(Player target, UUID requesterId) {
        OfflinePlayer requester = Bukkit.getOfflinePlayer(requesterId);
        String name = requester.getName();
        String displayName = "&%e" + (name != null ? name : requesterId) + " からの決闘申請";
        List<String> lore = List.of("&%a左クリックで承認", "&%c右クリックで拒否");
        return new GuiButton(GuiPlayerHead.build(requester, displayName, lore), (clicker, clickType) -> {
            boolean decline = clickType != null && clickType.contains("RIGHT");
            clicker.closeInventory();
            if (decline) {
                duelService.decline(clicker, requesterId);
                messages.send(clicker, "duel.declined");
                return;
            }
            Player requesterPlayer = Bukkit.getPlayer(requesterId);
            if (requesterPlayer == null) {
                messages.send(clicker, "duel.requester-offline");
                return;
            }
            DuelService.AcceptResult result = duelService.accept(clicker, requesterId, id -> java.util.Optional.ofNullable(Bukkit.getPlayer(id)));
            switch (result) {
                case OK -> messages.send(clicker, "duel.started");
                case NO_ARENA_FREE -> messages.send(clicker, "duel.no-arena-free");
                case NO_PENDING_REQUEST -> messages.send(clicker, "duel.no-pending-request");
            }
        });
    }
}
```

- [ ] **Step 2: Write DuelCommand**

```java
package rpg.extra.duel.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import rpg.core.command.TabCompletions;
import rpg.core.message.MessageManager;
import rpg.extra.duel.gui.DuelGuiScreen;
import rpg.extra.duel.listener.DuelDamageListener;
import rpg.extra.duel.manager.DuelSessionManager;
import rpg.extra.duel.model.DuelSession;
import rpg.extra.duel.service.DuelService;
import rpg.gui.framework.GuiManager;

import java.util.List;
import java.util.Optional;

/**
 * {@code /ol duel [gui|request <player>|accept [player]|decline [player]|cancel|forfeit]}.
 * Bare {@code /ol duel} (and {@code gui}) opens {@link DuelGuiScreen} - same gui-first default
 * every other general-player command in this jar now uses.
 */
public final class DuelCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("gui", "request", "accept", "decline", "cancel", "forfeit");

    private final DuelService duelService;
    private final DuelSessionManager sessionManager;
    private final DuelDamageListener damageListener;
    private final DuelGuiScreen guiScreen;
    private final GuiManager guiManager;
    private final MessageManager messages;

    public DuelCommand(DuelService duelService, DuelSessionManager sessionManager, DuelDamageListener damageListener,
                        DuelGuiScreen guiScreen, GuiManager guiManager, MessageManager messages) {
        this.duelService = duelService;
        this.sessionManager = sessionManager;
        this.damageListener = damageListener;
        this.guiScreen = guiScreen;
        this.guiManager = guiManager;
        this.messages = messages;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "command.player-only");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            guiManager.open(player, guiScreen.build(player));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "request" -> request(player, args);
            case "accept" -> accept(player, args);
            case "decline" -> decline(player, args);
            case "cancel" -> cancel(player, args);
            case "forfeit" -> forfeit(player);
            default -> messages.send(sender, "duel.usage");
        }
        return true;
    }

    private void request(Player player, String[] args) {
        if (args.length < 2) {
            messages.send(player, "duel.usage-request");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(player, "command.player-not-found", "player", args[1]);
            return;
        }
        DuelService.RequestResult result = duelService.request(player, target);
        switch (result) {
            case OK -> {
                messages.send(player, "duel.request-sent", "player", target.getName());
                messages.send(target, "duel.request-received", "player", player.getName());
            }
            case ALREADY_PENDING -> messages.send(player, "duel.already-pending");
            case ON_COOLDOWN -> messages.send(player, "duel.on-cooldown");
            case SELF -> messages.send(player, "duel.cannot-target-self");
        }
    }

    private void accept(Player player, String[] args) {
        java.util.UUID requesterId = null;
        if (args.length >= 2) {
            Player requester = Bukkit.getPlayerExact(args[1]);
            if (requester == null) {
                messages.send(player, "command.player-not-found", "player", args[1]);
                return;
            }
            requesterId = requester.getUniqueId();
        }
        DuelService.AcceptResult result = duelService.accept(player, requesterId, id -> Optional.ofNullable(Bukkit.getPlayer(id)));
        switch (result) {
            case OK -> messages.send(player, "duel.started");
            case NO_ARENA_FREE -> messages.send(player, "duel.no-arena-free");
            case NO_PENDING_REQUEST -> messages.send(player, "duel.no-pending-request");
        }
    }

    private void decline(Player player, String[] args) {
        java.util.UUID requesterId = null;
        if (args.length >= 2) {
            Player requester = Bukkit.getPlayerExact(args[1]);
            requesterId = requester != null ? requester.getUniqueId() : null;
        }
        boolean declined = duelService.decline(player, requesterId);
        messages.send(player, declined ? "duel.declined" : "duel.no-pending-request");
    }

    private void cancel(Player player, String[] args) {
        if (args.length < 2) {
            messages.send(player, "duel.usage-cancel");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            messages.send(player, "command.player-not-found", "player", args[1]);
            return;
        }
        boolean cancelled = duelService.cancel(player, target.getUniqueId());
        messages.send(player, cancelled ? "duel.cancelled" : "duel.no-pending-request");
    }

    private void forfeit(Player player) {
        Optional<DuelSession> session = sessionManager.sessionOf(player.getUniqueId());
        if (session.isEmpty()) {
            messages.send(player, "duel.not-in-duel");
            return;
        }
        damageListener.resolveDuel(session.get(), session.get().opponentOf(player.getUniqueId()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length <= 1) {
            return TabCompletions.matching(SUBCOMMANDS, args.length == 0 ? "" : args[0]);
        }
        if (args.length == 2 && List.of("request", "accept", "decline", "cancel").contains(args[0].toLowerCase())) {
            return TabCompletions.onlinePlayerNames(args[1]);
        }
        return List.of();
    }
}
```

- [ ] **Step 3: Build to confirm it compiles**

Run: `./gradlew build -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/rpg/extra/duel/gui/DuelGuiScreen.java \
        src/main/java/rpg/extra/duel/command/DuelCommand.java
git commit -m "Add DuelGuiScreen and /ol duel command"
```

---

### Task 11: DuelRankingGuiScreen + ranking subcommand

**Files:**
- Create: `src/main/java/rpg/extra/duel/gui/DuelRankingGuiScreen.java`
- Modify: `src/main/java/rpg/extra/duel/command/DuelCommand.java`

**Interfaces:**
- Consumes: `DuelStatsService` (Task 5).
- Produces: `DuelRankingGuiScreen.build()`; `DuelCommand` gains a `ranking` subcommand.

- [ ] **Step 1: Write DuelRankingGuiScreen**

```java
package rpg.extra.duel.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import rpg.extra.duel.repository.DuelStatsRepository.DuelStatsEntry;
import rpg.extra.duel.service.DuelStatsService;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.gui.framework.GuiPlayerHead;

import java.util.List;

/** Top-27 duel-win leaderboard - mirrors rpg.extra.ranking.gui.RankingGuiScreen's own single-flat-list shape. */
public final class DuelRankingGuiScreen {

    private final DuelStatsService statsService;

    public DuelRankingGuiScreen(DuelStatsService statsService) {
        this.statsService = statsService;
    }

    public Gui build() {
        Gui gui = new Gui("&%8決闘ランキング", 27);
        List<DuelStatsEntry> top = statsService.topByWins(27);
        int slot = 0;
        int rank = 1;
        for (DuelStatsEntry entry : top) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(entry.uuid());
            String name = player.getName();
            String displayName = "&%e#" + rank + " " + (name != null ? name : entry.uuid());
            List<String> lore = List.of("&%a勝利: &%f" + entry.wins(), "&%c敗北: &%f" + entry.losses());
            gui.set(slot++, GuiButton.display(GuiPlayerHead.build(player, displayName, lore)));
            rank++;
        }
        if (top.isEmpty()) {
            gui.set(13, GuiButton.display(new rpg.util.ItemBuilder(Material.PAPER).name("&%7まだ決闘の記録がありません").build()));
        }
        return gui;
    }
}
```

- [ ] **Step 2: Wire the `ranking` subcommand into DuelCommand**

In `src/main/java/rpg/extra/duel/command/DuelCommand.java`, apply these exact changes:

Add the import alongside the existing ones:

```java
import rpg.extra.duel.gui.DuelRankingGuiScreen;
```

Replace the `SUBCOMMANDS` constant:

```java
    private static final List<String> SUBCOMMANDS = List.of("gui", "request", "accept", "decline", "cancel", "forfeit", "ranking");
```

Add a field and grow the constructor:

```java
    private final DuelService duelService;
    private final DuelSessionManager sessionManager;
    private final DuelDamageListener damageListener;
    private final DuelGuiScreen guiScreen;
    private final DuelRankingGuiScreen rankingScreen;
    private final GuiManager guiManager;
    private final MessageManager messages;

    public DuelCommand(DuelService duelService, DuelSessionManager sessionManager, DuelDamageListener damageListener,
                        DuelGuiScreen guiScreen, DuelRankingGuiScreen rankingScreen, GuiManager guiManager, MessageManager messages) {
        this.duelService = duelService;
        this.sessionManager = sessionManager;
        this.damageListener = damageListener;
        this.guiScreen = guiScreen;
        this.rankingScreen = rankingScreen;
        this.guiManager = guiManager;
        this.messages = messages;
    }
```

Add a case to the `switch` in `onCommand` (alongside `request`/`accept`/`decline`/`cancel`/`forfeit`):

```java
            case "ranking" -> guiManager.open(player, rankingScreen.build());
```

- [ ] **Step 3: Build to confirm it compiles**

Run: `./gradlew build -q`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/main/java/rpg/extra/duel/gui/DuelRankingGuiScreen.java \
        src/main/java/rpg/extra/duel/command/DuelCommand.java
git commit -m "Add DuelRankingGuiScreen and /ol duel ranking subcommand"
```

---

### Task 12: DuelModule wiring + config/messages + module registration

**Files:**
- Create: `src/main/java/rpg/extra/duel/DuelModule.java`
- Modify: `src/main/java/rpg/core/OreliaPlugin.java` (module registration order)
- Modify: `src/main/resources/config.yml` (add `duel:` section, bump `config-version`)
- Modify: `src/main/resources/messages.yml` (add `duel:` section, bump `config-version`)
- Modify: `CHANGELOG.md`

**Interfaces:**
- Consumes: every class from Tasks 1-11.
- Produces: `DuelModule implements RpgModule`, registered in `OreliaPlugin`'s module list.

- [ ] **Step 1: Add the `duel:` section to config.yml**

Add near the other `extra`-layer module sections (alongside `party:`/`friend:`/`guild:` blocks):

```yaml
duel:
  # Seconds a player must wait between sending duel requests.
  cooldown-seconds: 60
  # Money paid to the winner from the server (Vault), not a player-vs-player wager.
  reward-money: 50
```

Bump `config.yml`'s `config-version` by 1.

- [ ] **Step 2: Add the `duel:` section to messages.yml**

```yaml
duel:
  usage: "&%e使い方: /duel &%7<&%erequest&%7|&%eaccept&%7|&%edecline&%7|&%ecancel&%7|&%eforfeit&%7|&%eranking&%7>&%e"
  usage-request: "&%e使い方: /duel request &%7<&%eplayer&%7>&%e"
  usage-cancel: "&%e使い方: /duel cancel &%7<&%eplayer&%7>&%e"
  request-sent: "&%a{player}に決闘を申請しました。"
  request-received: "&%e{player}から決闘の申請が届きました。&%7/duel accept &%f{player}&%7 で承認できます。"
  already-pending: "&%cその相手には既に決闘を申請中です。"
  on-cooldown: "&%c決闘の申請はクールダウン中です。"
  cannot-target-self: "&%c自分自身に決闘を申し込むことはできません。"
  started: "&%a決闘が始まりました！"
  no-arena-free: "&%c空いている決闘アリーナがありません。しばらく待ってから再度お試しください。"
  no-pending-request: "&%c該当する決闘申請がありません。"
  declined: "&%e決闘の申請を拒否しました。"
  cancelled: "&%e決闘の申請を取り消しました。"
  requester-offline: "&%c申請者がオフラインです。"
  not-in-duel: "&%c現在決闘中ではありません。"
  won: "&%a{opponent}との決闘に勝利しました！ &%6+{reward}G"
  lost: "&%c{opponent}との決闘に敗北しました。"
  admin:
    arena-usage: "&%e使い方: /oladmin duelarena &%7<&%eadd&%7|&%eset&%7|&%eremove&%7|&%elist&%7>&%e"
    arena-added: "&%a決闘アリーナを追加しました。"
    arena-set: "&%aアリーナ{index}番を現在地に設定しました。"
    arena-removed: "&%aアリーナ{index}番を削除しました。"
    arena-index-out-of-range: "&%c指定したインデックスのアリーナは存在しません。"
    arena-list-header: "&%a決闘アリーナ一覧:"
    arena-list-entry: "&%7{index}: &%f{world} ({x}, {y}, {z})"
```

Bump `messages.yml`'s `config-version` by 1 (it's currently 19 after Task-list phase 3's job-gui change earlier this session - confirm the current value with `grep config-version src/main/resources/messages.yml` before bumping, in case another change landed in between).

- [ ] **Step 3: Write DuelModule**

```java
package rpg.extra.duel;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.api.StatusApi;
import rpg.core.OreliaPlugin;
import rpg.core.command.CommandAliasUtil;
import rpg.core.module.RpgModule;
import rpg.database.manager.DatabaseManager;
import rpg.extra.duel.command.DuelArenaAdminCommand;
import rpg.extra.duel.command.DuelCommand;
import rpg.extra.duel.gui.DuelGuiScreen;
import rpg.extra.duel.gui.DuelRankingGuiScreen;
import rpg.extra.duel.listener.DuelDamageListener;
import rpg.extra.duel.listener.DuelQuitListener;
import rpg.extra.duel.manager.DuelRequestManager;
import rpg.extra.duel.manager.DuelSessionManager;
import rpg.extra.duel.repository.DuelArenaRepository;
import rpg.extra.duel.repository.DuelStatsRepository;
import rpg.extra.duel.service.DuelArenaAdminService;
import rpg.extra.duel.service.DuelService;
import rpg.extra.duel.service.DuelStatsService;
import rpg.gui.framework.GuiManager;

import java.util.logging.Level;

/**
 * Duel module: 1v1 duel requests, multi-arena registration, HP-threshold duel resolution with
 * no real death, a small server-funded money reward, and a simple win/loss leaderboard (SOW
 * follow-up, see docs/superpowers/specs/2026-08-30-duel-module-design.md).
 */
public final class DuelModule implements RpgModule {

    private final DuelArenaRepository arenaRepository = new DuelArenaRepository();
    private final DuelRequestManager requestManager = new DuelRequestManager();
    private final DuelSessionManager sessionManager = new DuelSessionManager(arenaRepository);
    private final GuiManager guiManager = new GuiManager();
    private OreliaPlugin plugin;

    @Override
    public String getName() {
        return "duel";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        this.plugin = plugin;
        DatabaseManager databaseManager = plugin.getServer().getServicesManager().load(DatabaseManager.class);
        if (databaseManager == null) {
            throw new IllegalStateException("duel module requires OreliaCore's DatabaseManager");
        }
        Economy economy = plugin.getServer().getServicesManager().load(Economy.class);
        if (economy == null) {
            throw new IllegalStateException("duel module requires Vault's Economy service");
        }
        StatusApi statusApi = plugin.getServer().getServicesManager().load(StatusApi.class);
        if (statusApi == null) {
            throw new IllegalStateException("duel module requires OreliaCore's StatusApi");
        }

        reloadArenas();

        DuelStatsRepository statsRepository = new DuelStatsRepository(databaseManager);
        try {
            statsRepository.createSchemaIfNotExists();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize duel schema", e);
        }
        DuelStatsService statsService = new DuelStatsService(statsRepository);

        YamlConfiguration config = plugin.getConfigManager().get("config.yml").get();
        long cooldownSeconds = config.getLong("duel.cooldown-seconds", 60);
        double rewardMoney = config.getDouble("duel.reward-money", 50);

        DuelService duelService = new DuelService(requestManager, sessionManager, cooldownSeconds);
        DuelDamageListener damageListener = new DuelDamageListener(sessionManager, statsService, statusApi, economy,
                plugin.getMessageManager(), rewardMoney);
        DuelQuitListener quitListener = new DuelQuitListener(sessionManager, requestManager, damageListener);
        plugin.getServer().getPluginManager().registerEvents(damageListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(quitListener, plugin);

        DuelGuiScreen guiScreen = new DuelGuiScreen(requestManager, duelService, plugin.getMessageManager());
        DuelRankingGuiScreen rankingScreen = new DuelRankingGuiScreen(statsService);
        DuelCommand duelCommand = new DuelCommand(duelService, sessionManager, damageListener, guiScreen, rankingScreen,
                guiManager, plugin.getMessageManager());
        String description = "決闘画面を開きます。";
        String usage = "duel [gui|request <player>|accept [player]|decline [player]|cancel <player>|forfeit|ranking]";
        plugin.getPlayerCommandRegistry().register("duel", duelCommand, description, usage);
        CommandAliasUtil.registerAlias(plugin, "duel", duelCommand, description,
                "[gui|request <player>|accept [player]|decline [player]|cancel <player>|forfeit|ranking]");

        DuelArenaAdminService arenaAdminService = new DuelArenaAdminService(arenaRepository, plugin.getConfigManager());
        DuelArenaAdminCommand arenaAdminCommand = new DuelArenaAdminCommand(arenaAdminService, plugin.getMessageManager());
        plugin.getAdminCommandRegistry().register("duelarena", arenaAdminCommand,
                "決闘アリーナを追加・削除します。", "duelarena add|set <index>|remove <index>|list");
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onReload() {
        reloadArenas();
    }

    private void reloadArenas() {
        plugin.getConfigManager().register("duels.yml");
        YamlConfiguration config = plugin.getConfigManager().get("duels.yml").get();
        arenaRepository.load(config);
    }
}
```

- [ ] **Step 4: Register DuelModule in OreliaPlugin's module list**

In `src/main/java/rpg/core/OreliaPlugin.java`, add the import alongside the other `rpg.extra.*` module imports (currently lines 45-57):

```java
import rpg.extra.chat.ChatModule;
import rpg.extra.duel.DuelModule;
import rpg.extra.trade.TradeModule;
```

(i.e. insert the `DuelModule` import between the existing `ChatModule` and `TradeModule` imports, keeping the rest of that import block unchanged.)

Then insert one registration call between the existing `ChatModule`/`TradeModule` registrations (currently lines 177-178) - no module in the social/economy block depends on Duel, and Duel itself only needs foundation-block `StatusApi`/`Economy`/`DatabaseManager` (all already registered by this point), so any spot in this block works; right after `Chat` keeps the diff small:

```java
        moduleManager.register(new ChatModule());
        moduleManager.register(new DuelModule());
        moduleManager.register(new TradeModule());
```

- [ ] **Step 5: Update CHANGELOG.md**

Add one line near the top of the `## 更新履歴` list:

```
- PvP決闘機能を追加(`/ol duel`)。決闘申請・承認、複数登録可能な専用アリーナ、HP0到達で実際には死なせず終了、勝者への少額報酬、勝敗数ランキング(`/ol duel ranking`)。
```

- [ ] **Step 6: Full build and manual verification checklist**

Run: `./gradlew build`
Expected: BUILD SUCCESSFUL, all tests green (including the new `DuelArenaAllocatorTest`/`DuelStatsServiceTest`)

Manual verification checklist (run against a real server before merging, same as every other PR this session has flagged for manual review):
- `/oladmin duelarena add` while standing somewhere registers arena 1
- `/ol duel request <player>` sends a request, target sees `duel.request-received`
- `/ol duel accept` (or the GUI's accept button) teleports both to the arena
- Fighting until one player's scaled HP would hit 0 ends the duel without a death screen, teleports both back, heals both, pays the winner, and both `duel.won`/`duel.lost` messages show
- `/ol duel ranking` shows the updated win count
- Requesting a duel while no arena is free reports `duel.no-arena-free` on accept
- Quitting mid-duel ends it as a forfeit for the departing player

- [ ] **Step 7: Commit**

```bash
git add src/main/java/rpg/extra/duel/DuelModule.java \
        src/main/java/rpg/core/OreliaPlugin.java \
        src/main/resources/config.yml \
        src/main/resources/messages.yml \
        CHANGELOG.md
git commit -m "Wire DuelModule into the plugin (config, messages, module registration)"
```

---

## Self-Review Notes

- **Spec coverage:** request/accept/decline/cancel flow (Task 7/10), multi-arena registration (Tasks 1-3), HP-threshold resolution with no real death (Task 8), server-funded reward (Task 8/12), win/loss leaderboard not folded into `RankingModule` (Tasks 4/5/11), forfeit-on-quit (Task 9) - every spec section maps to a task.
- **Out-of-scope items** (ELO, wagers, spectating) have no task, matching the spec's explicit exclusion.
- **Type consistency checked:** `DuelSession`/`DuelArena`/`DuelStatsRepository.DuelStatsEntry` field names and `DuelService.RequestResult`/`AcceptResult` enum constants are used identically across every task that references them (Task 7 defines them, Tasks 8/10/11 consume them unchanged).
