package rpg.extra.mail.config;

import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Retention/inbox-size limits for the mail module (SOW MailModule follow-up) - previously
 * hardcoded nowhere at all, since there was no way to send player-to-player mail to fill up
 * an inbox in the first place.
 */
public final class MailConfig {

    private int maxRetainedPerPlayer = 100;
    private int retentionDays = 30;
    private long purgeCheckPeriodTicks = 20L * 60 * 60;
    private boolean notifySoundEnabled = true;
    private String notifySoundName = "ENTITY_EXPERIENCE_ORB_PICKUP";
    private double notifySoundVolume = 1.0;
    private double notifySoundPitch = 1.0;

    public void load(YamlConfiguration config) {
        maxRetainedPerPlayer = config.getInt("mail.max-retained-per-player", 100);
        retentionDays = config.getInt("mail.retention-days", 30);
        purgeCheckPeriodTicks = config.getLong("mail.purge-check-period-ticks", 20L * 60 * 60);
        notifySoundEnabled = config.getBoolean("mail.notify-sound.enabled", true);
        notifySoundName = config.getString("mail.notify-sound.name", "ENTITY_EXPERIENCE_ORB_PICKUP");
        notifySoundVolume = config.getDouble("mail.notify-sound.volume", 1.0);
        notifySoundPitch = config.getDouble("mail.notify-sound.pitch", 1.0);
    }

    public int getMaxRetainedPerPlayer() {
        return maxRetainedPerPlayer;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public long getPurgeCheckPeriodTicks() {
        return purgeCheckPeriodTicks;
    }

    public boolean isNotifySoundEnabled() {
        return notifySoundEnabled;
    }

    public String getNotifySoundName() {
        return notifySoundName;
    }

    public double getNotifySoundVolume() {
        return notifySoundVolume;
    }

    public double getNotifySoundPitch() {
        return notifySoundPitch;
    }
}
