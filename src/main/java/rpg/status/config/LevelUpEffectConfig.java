package rpg.status.config;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Loads {@code config.yml: status.level-up-effect.*} - the sound/particle played when a
 * character or job level-up is announced ({@link rpg.status.service.LevelUpFeedbackService}).
 * Sound/particle names are resolved via {@code Sound.valueOf}/{@code Particle.valueOf} at
 * playback time - an invalid name just skips that effect rather than throwing.
 */
public final class LevelUpEffectConfig {

    private boolean enabled = true;
    private String sound = "ENTITY_PLAYER_LEVELUP";
    private String particle = "TOTEM";

    public void load(YamlConfiguration config) {
        enabled = config.getBoolean("status.level-up-effect.enabled", true);
        sound = config.getString("status.level-up-effect.sound", "ENTITY_PLAYER_LEVELUP");
        particle = config.getString("status.level-up-effect.particle", "TOTEM");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getSound() {
        return sound;
    }

    public String getParticle() {
        return particle;
    }
}
