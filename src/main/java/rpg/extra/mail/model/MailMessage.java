package rpg.extra.mail.model;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * One in-game mail message (SOW MailModule), possibly with item attachments. {@code
 * senderName} is null for system-sent mail (e.g. a future auction "your item sold" notice).
 */
public final class MailMessage {

    private final UUID id;
    private final UUID recipientId;
    private final String senderName;
    private final String subject;
    private final String body;
    private final ItemStack[] attachments;
    private final long sentAtMillis;
    private boolean read;
    private boolean claimed;

    public MailMessage(UUID id, UUID recipientId, String senderName, String subject, String body,
                        ItemStack[] attachments, long sentAtMillis, boolean read, boolean claimed) {
        this.id = id;
        this.recipientId = recipientId;
        this.senderName = senderName;
        this.subject = subject;
        this.body = body;
        this.attachments = attachments;
        this.sentAtMillis = sentAtMillis;
        this.read = read;
        this.claimed = claimed;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRecipientId() {
        return recipientId;
    }

    public String getSenderName() {
        return senderName == null ? "システム" : senderName;
    }

    public String getSubject() {
        return subject;
    }

    public String getBody() {
        return body;
    }

    public ItemStack[] getAttachments() {
        return attachments;
    }

    public boolean hasAttachments() {
        for (ItemStack stack : attachments) {
            if (stack != null && !stack.getType().isAir()) {
                return true;
            }
        }
        return false;
    }

    public long getSentAtMillis() {
        return sentAtMillis;
    }

    public boolean isRead() {
        return read;
    }

    public void setRead(boolean read) {
        this.read = read;
    }

    public boolean isClaimed() {
        return claimed;
    }

    public void setClaimed(boolean claimed) {
        this.claimed = claimed;
    }
}
