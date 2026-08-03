package rpg.world.cutscene.model;

/**
 * One timed beat of a cutscene, fired {@code delayTicks} after playback starts. Which
 * fields are meaningful depends on {@link #getType()}.
 */
public final class CutSceneStep {

    private final CutSceneStepType type;
    private final long delayTicks;
    private final String message;
    private final String title;
    private final String subtitle;
    private final String effectId;
    private final String cameraWorld;
    private final double cameraX;
    private final double cameraY;
    private final double cameraZ;
    private final float cameraYaw;
    private final float cameraPitch;

    public CutSceneStep(CutSceneStepType type, long delayTicks, String message, String title, String subtitle,
                         String effectId, String cameraWorld, double cameraX, double cameraY, double cameraZ,
                         float cameraYaw, float cameraPitch) {
        this.type = type;
        this.delayTicks = delayTicks;
        this.message = message;
        this.title = title;
        this.subtitle = subtitle;
        this.effectId = effectId;
        this.cameraWorld = cameraWorld;
        this.cameraX = cameraX;
        this.cameraY = cameraY;
        this.cameraZ = cameraZ;
        this.cameraYaw = cameraYaw;
        this.cameraPitch = cameraPitch;
    }

    public CutSceneStepType getType() {
        return type;
    }

    public long getDelayTicks() {
        return delayTicks;
    }

    public String getMessage() {
        return message;
    }

    public String getTitle() {
        return title;
    }

    public String getSubtitle() {
        return subtitle;
    }

    public String getEffectId() {
        return effectId;
    }

    public String getCameraWorld() {
        return cameraWorld;
    }

    public double getCameraX() {
        return cameraX;
    }

    public double getCameraY() {
        return cameraY;
    }

    public double getCameraZ() {
        return cameraZ;
    }

    public float getCameraYaw() {
        return cameraYaw;
    }

    public float getCameraPitch() {
        return cameraPitch;
    }
}
