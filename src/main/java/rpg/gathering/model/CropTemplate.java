package rpg.gathering.model;

import org.bukkit.Material;

/** Config-driven definition of one farmable crop (SOW 3.2 {@code farm-settings.*}). */
public record CropTemplate(Material cropType, Material seedItem, int xpGain) {
}
