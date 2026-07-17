package rpg.core.message;

import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import rpg.core.config.ConfigFile;
import rpg.util.ColorUtil;

/**
 * Key-based lookup over {@code messages.yml}, replacing the direct {@code ChatColor + "..."}
 * literals that used to be scattered across command/listener/service classes. Every plugin
 * (core/world/extra/debug) owns its own instance over its own {@code messages.yml}, registered
 * through the same {@link rpg.core.config.ConfigManager} every other config file goes through.
 *
 * <p>Keys are dotted paths into the YAML tree (e.g. {@code "job.changed"}). Placeholders in the
 * template are {@code {name}} tokens, replaced positionally from the varargs passed to
 * {@link #format}/{@link #send} (alternating placeholder name, value). Missing keys fall back to
 * the key itself wrapped in {@code ??}, so a typo shows up in-game instead of throwing.
 */
public final class MessageManager {

    private final ConfigFile messagesFile;

    public MessageManager(ConfigFile messagesFile) {
        this.messagesFile = messagesFile;
    }

    public String getPrefix() {
        return ColorUtil.colorize(messagesFile.get().getString("prefix", ""));
    }

    /** Raw (uncolorized, unprefixed) template text for {@code key}, or {@code "??key??"} if missing. */
    public String raw(String key) {
        String value = getString(key);
        return value != null ? value : "??" + key + "??";
    }

    /**
     * Colorized, placeholder-substituted text for {@code key}. {@code placeholders} alternates
     * {@code name, value, name, value, ...}; a {@code {name}} token in the template is replaced
     * with {@code String.valueOf(value)}.
     */
    public String format(String key, Object... placeholders) {
        String template = raw(key);
        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            template = template.replace("{" + placeholders[i] + "}", String.valueOf(placeholders[i + 1]));
        }
        return ColorUtil.colorize(template);
    }

    /** Sends {@link #getPrefix()} + {@link #format(String, Object...)} to {@code sender} as an Adventure {@link Component}. */
    public void send(CommandSender sender, String key, Object... placeholders) {
        sender.sendMessage(ColorUtil.component(getPrefix() + format(key, placeholders)));
    }

    /** Like {@link #send} but without the {@code prefix} (for multi-line lists, GUI titles, ...). */
    public void sendRaw(CommandSender sender, String key, Object... placeholders) {
        sender.sendMessage(ColorUtil.component(format(key, placeholders)));
    }

    private String getString(String dottedKey) {
        return messagesFile.get().getString(dottedKey);
    }
}
