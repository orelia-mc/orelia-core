package rpg.gathering.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Experience curve for the level shared by mining, woodcutting, and farming
 * ({@code gathering.yml: leveling}). Supports either an explicit per-level table or a
 * formula ({@code NextXP = a * level^b + c}) so a future level-cap increase (SOW 3.3) is a
 * config-only change - no code edit needed.
 */
public final class GatheringLevelingConfig {

    public enum Mode {
        TABLE,
        FORMULA
    }

    private Mode mode = Mode.FORMULA;
    private double formulaA = 50;
    private double formulaB = 1.5;
    private double formulaC = 100;
    private List<Long> table = new ArrayList<>();
    private int maxLevel = 50;

    public GatheringLevelingConfig() {
    }

    public GatheringLevelingConfig(Mode mode, double formulaA, double formulaB, double formulaC, List<Long> table, int maxLevel) {
        this.mode = mode;
        this.formulaA = formulaA;
        this.formulaB = formulaB;
        this.formulaC = formulaC;
        this.table = table;
        this.maxLevel = maxLevel;
    }

    public void load(YamlConfiguration config) {
        this.mode = "TABLE".equalsIgnoreCase(config.getString("leveling.mode", "FORMULA")) ? Mode.TABLE : Mode.FORMULA;
        this.formulaA = config.getDouble("leveling.formula.a", 50);
        this.formulaB = config.getDouble("leveling.formula.b", 1.5);
        this.formulaC = config.getDouble("leveling.formula.c", 100);
        this.table = config.getLongList("leveling.table");
        this.maxLevel = config.getInt("leveling.max-level", 50);
    }

    /** Total experience required to advance from {@code level} to {@code level + 1}. */
    public long requiredExperience(int level) {
        if (mode == Mode.TABLE && !table.isEmpty()) {
            int index = Math.min(Math.max(level - 1, 0), table.size() - 1);
            return table.get(index);
        }
        return Math.round(formulaA * Math.pow(level, formulaB) + formulaC);
    }

    public int getMaxLevel() {
        return maxLevel;
    }
}
