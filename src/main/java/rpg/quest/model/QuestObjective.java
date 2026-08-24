package rpg.quest.model;

/**
 * One objective row. {@code targetId} means different things per {@link ObjectiveType}:
 * a monster/boss/npc/dungeon id, a weapon id or vanilla material name for item
 * objectives, or unused (null) for {@link ObjectiveType#REACH_LOCATION} which instead
 * uses the world/x/y/z/radius fields.
 */
public final class QuestObjective {

    private final ObjectiveType type;
    private final String targetId;
    private final int requiredAmount;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final double radius;

    public QuestObjective(ObjectiveType type, String targetId, int requiredAmount,
                           String world, double x, double y, double z, double radius) {
        this.type = type;
        this.targetId = targetId;
        this.requiredAmount = requiredAmount;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.radius = radius;
    }

    public ObjectiveType getType() {
        return type;
    }

    public String getTargetId() {
        return targetId;
    }

    public int getRequiredAmount() {
        return requiredAmount;
    }

    public String getWorld() {
        return world;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public double getRadius() {
        return radius;
    }
}
