package rpg.extra.mail.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.Sound;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.entity.Player;
import rpg.core.message.MessageManager;
import rpg.core.scheduler.SchedulerService;
import rpg.extra.chat.model.ChatBadge;
import rpg.extra.chat.service.ChatMuteService;
import rpg.extra.mail.config.MailConfig;
import rpg.extra.mail.service.MailService;
import rpg.util.ColorUtil;

import java.util.logging.Logger;

/**
 * Tells a player about unread mail shortly after they join, rather than requiring them to
 * think to open {@code /ol mail}. The notice is clickable ({@code /ol mail} runs on click,
 * see {@link rpg.util.ColorUtil#componentWithCommand}) instead of being plain text - one of
 * the few "make chat a bit less static" changes bundled with this mail work.
 */
public final class MailUnreadJoinListener implements Listener {

    private static final long NOTICE_DELAY_TICKS = 40L;

    private final MailService mailService;
    private final MessageManager messages;
    private final SchedulerService schedulerService;
    private final MailConfig mailConfig;
    private final Logger logger;
    private final ChatMuteService muteService;

    public MailUnreadJoinListener(MailService mailService, MessageManager messages, SchedulerService schedulerService,
            MailConfig mailConfig, Logger logger, ChatMuteService muteService) {
        this.mailService = mailService;
        this.messages = messages;
        this.schedulerService = schedulerService;
        this.mailConfig = mailConfig;
        this.logger = logger;
        this.muteService = muteService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        schedulerService.runLater(() -> {
            if (!player.isOnline() || muteService.isMuted(player.getUniqueId(), ChatBadge.SYSTEM)) {
                return;
            }
            long unread = mailService.getInbox(player.getUniqueId()).stream().filter(m -> !m.isRead()).count();
            if (unread <= 0) {
                return;
            }
            String text = messages.getPrefix() + messages.format("mail.unread-count", "count", unread);
            Component line = ChatBadge.SYSTEM.decorate(ColorUtil.componentWithCommand(text, "/ol mail"));
            player.sendMessage(line);
            playNotifySound(player);
        }, NOTICE_DELAY_TICKS);
    }

    private void playNotifySound(Player player) {
        if (!mailConfig.isNotifySoundEnabled()) {
            return;
        }
        Sound sound;
        try {
            sound = Sound.valueOf(mailConfig.getNotifySoundName().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warning("mail.notify-sound.name (\"" + mailConfig.getNotifySoundName()
                    + "\") isn't a valid org.bukkit.Sound constant - skipping the mail notify sound.");
            return;
        }
        player.playSound(player.getLocation(), sound,
                (float) mailConfig.getNotifySoundVolume(), (float) mailConfig.getNotifySoundPitch());
    }
}
