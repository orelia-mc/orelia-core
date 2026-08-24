package rpg.extra.housing.model;

/**
 * Static house plot definition loaded from {@code housing.yml} (SOW HousingModule). Like
 * orelia-world's dungeon entries, this is a physical teleport point, not a generated
 * per-player instance.
 */
public final class HousePlot {

    private final String id;
    private final String name;
    private final double price;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;

    public HousePlot(String id, String name, double price, String world, double x, double y, double z, float yaw) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public double getPrice() {
        return price;
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

    public float getYaw() {
        return yaw;
    }
}
