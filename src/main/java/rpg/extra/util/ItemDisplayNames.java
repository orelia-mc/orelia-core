package rpg.extra.util;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

/**
 * A readable label for an {@link ItemStack} - its custom display name if it has one, otherwise
 * a title-cased Material name ("DIAMOND_SWORD" -> "Diamond Sword") instead of the raw enum
 * constant. Shared by every module that shows an item name in chat/GUI text (trade, auction, ...).
 */
public final class ItemDisplayNames {

    private ItemDisplayNames() {
    }

    public static String of(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName());
        }
        String[] words = item.getType().name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return result.toString();
    }
}
