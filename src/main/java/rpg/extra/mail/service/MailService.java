package rpg.extra.mail.service;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import rpg.extra.mail.config.MailConfig;
import rpg.extra.mail.model.MailMessage;
import rpg.extra.mail.repository.MailRepository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Send/list/claim mail (SOW MailModule).
 */
public final class MailService {

    public enum SendResult {
        OK, RECIPIENT_INBOX_FULL
    }

    private final MailRepository repository;
    private final MailConfig config;

    public MailService(MailRepository repository, MailConfig config) {
        this.repository = repository;
        this.config = config;
    }

    public void send(UUID recipientId, String senderName, String subject, String body, ItemStack... attachments) {
        repository.send(recipientId, senderName, subject, body, attachments);
    }

    /**
     * Player-to-player send (as opposed to {@link #send}, used for system notices like
     * auction sale mail) - gated on the recipient's inbox not already being at
     * {@link MailConfig#getMaxRetainedPerPlayer()}, since unlike a system notice this can be
     * triggered by another player at will.
     */
    public SendResult sendFromPlayer(Player sender, Player recipient, String subject, String body) {
        if (repository.countByRecipient(recipient.getUniqueId()) >= config.getMaxRetainedPerPlayer()) {
            return SendResult.RECIPIENT_INBOX_FULL;
        }
        repository.send(recipient.getUniqueId(), sender.getName(), subject, body, new ItemStack[0]);
        return SendResult.OK;
    }

    /**
     * Deletes read, fully-resolved mail older than {@link MailConfig#getRetentionDays()}.
     * A message with unclaimed attachments is kept regardless of age - losing an attachment
     * to a background cleanup task would be far more surprising than an inbox slowly growing.
     */
    public void purgeExpired() {
        long cutoffMillis = System.currentTimeMillis() - Duration.ofDays(config.getRetentionDays()).toMillis();
        for (MailMessage message : repository.findOlderThan(cutoffMillis)) {
            if (message.isRead() && (!message.hasAttachments() || message.isClaimed())) {
                repository.delete(message.getId());
            }
        }
    }

    public List<MailMessage> getInbox(UUID recipientId) {
        return repository.findByRecipient(recipientId);
    }

    public void markRead(MailMessage message) {
        if (!message.isRead()) {
            message.setRead(true);
            repository.save(message);
        }
    }

    /** Gives the message's attachments to {@code player} (dropping any that don't fit) and marks it claimed. */
    public boolean claim(Player player, MailMessage message) {
        if (message.isClaimed()) {
            return false;
        }
        for (ItemStack item : message.getAttachments()) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            player.getInventory().addItem(item).values()
                    .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        }
        message.setClaimed(true);
        message.setRead(true);
        repository.save(message);
        return true;
    }

    public void delete(MailMessage message) {
        repository.delete(message.getId());
    }

    public Optional<MailMessage> findById(UUID recipientId, UUID mailId) {
        return getInbox(recipientId).stream().filter(m -> m.getId().equals(mailId)).findFirst();
    }
}
