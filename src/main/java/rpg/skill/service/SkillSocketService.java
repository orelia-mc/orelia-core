package rpg.skill.service;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reads/writes which skill ids are socketed into a weapon's skill slots. Stored as a
 * single {@code;}-delimited string in the item's PersistentDataContainer since Bukkit has
 * no built-in list PersistentDataType.
 */
public final class SkillSocketService {

    private final NamespacedKey socketedSkillsKey;

    public SkillSocketService(Plugin plugin) {
        this.socketedSkillsKey = new NamespacedKey(plugin, "socketed_skills");
    }

    public List<String> getSocketedSkills(ItemStack weapon) {
        if (weapon == null || !weapon.hasItemMeta()) {
            return List.of();
        }
        String raw = weapon.getItemMeta().getPersistentDataContainer().get(socketedSkillsKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.asList(raw.split(";"));
    }

    public boolean hasSkill(ItemStack weapon, String skillId) {
        return getSocketedSkills(weapon).contains(skillId);
    }

    /**
     * Adds {@code skillId} to the weapon's sockets, respecting {@code maxSlots}. Returns
     * false (no change) if the weapon has no free slot or already has this skill socketed.
     */
    public boolean socket(ItemStack weapon, String skillId, int maxSlots) {
        List<String> current = new ArrayList<>(getSocketedSkills(weapon));
        if (current.contains(skillId) || current.size() >= maxSlots) {
            return false;
        }
        current.add(skillId);
        write(weapon, current);
        return true;
    }

    private void write(ItemStack weapon, List<String> skillIds) {
        ItemMeta meta = weapon.getItemMeta();
        meta.getPersistentDataContainer().set(socketedSkillsKey, PersistentDataType.STRING, String.join(";", skillIds));
        weapon.setItemMeta(meta);
    }
}
