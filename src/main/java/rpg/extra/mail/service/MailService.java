package rpg.extra.mail.service;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import rpg.extra.mail.model.MailMessage;
import rpg.extra.mail.repository.MailRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Send/list/claim mail (SOW MailModule).
 */
public final class MailService {

    private final MailRepository repository;

    public MailService(MailRepository repository) {
        this.repository = repository;
    }

    public void send(UUID recipientId, String senderName, String subject, String body, ItemStack... attachments) {
        repository.send(recipientId, senderName, subject, body, attachments);
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
