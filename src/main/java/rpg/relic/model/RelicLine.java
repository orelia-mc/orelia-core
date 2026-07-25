package rpg.relic.model;

import rpg.item.model.ElementType;

/**
 * One stat line on a relic (either its main stat, or one of up to 4 substats).
 * {@code element} is only meaningful when {@code type == ELEMENTAL_DMG_PERCENT} - rolled once
 * at generation and fixed for the relic's lifetime, {@link ElementType#NONE} otherwise.
 */
public record RelicLine(RelicStatType type, ElementType element, double value) {
}
