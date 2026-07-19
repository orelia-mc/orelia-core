package rpg.gui.screen;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import rpg.accessory.model.AccessoryData;
import rpg.accessory.repository.AccessoryRepository;
import rpg.accessory.service.AccessoryFactory;
import rpg.api.ShopEntry;
import rpg.core.message.MessageManager;
import rpg.economy.service.EconomyService;
import rpg.gui.config.GuiConfig;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.item.manager.ItemManager;
import rpg.item.model.WeaponData;
import rpg.util.ItemBuilder;

import java.util.List;

/**
 * NPC shop screen (SOW section 17 "NPCショップ") shared by the weapon/armor/accessory
 * shop NPC types - which items are for sale is decided by the npc module, not here.
 */
public final class ShopGuiScreen {

    private final ItemManager itemManager;
    private final AccessoryRepository accessoryRepository;
    private final AccessoryFactory accessoryFactory;
    private final EconomyService economyService;
    private final GuiConfig guiConfig;
    private final MessageManager messages;

    public ShopGuiScreen(ItemManager itemManager, AccessoryRepository accessoryRepository,
                          AccessoryFactory accessoryFactory, EconomyService economyService, GuiConfig guiConfig,
                          MessageManager messages) {
        this.itemManager = itemManager;
        this.accessoryRepository = accessoryRepository;
        this.accessoryFactory = accessoryFactory;
        this.economyService = economyService;
        this.guiConfig = guiConfig;
        this.messages = messages;
    }

    public Gui build(Player player, List<ShopEntry> stock) {
        Gui gui = new Gui(guiConfig.title("shop", "&%8NPCショップ"), 54);
        int slot = 0;
        for (ShopEntry entry : stock) {
            ItemStack preview = resolve(entry).orElse(null);
            if (preview == null || slot >= 54) {
                continue;
            }
            String displayName = displayNameOf(entry);
            ItemStack icon = new ItemBuilder(preview.getType())
                    .name(Component.text(displayName))
                    .lore("&%7価格: " + formatPrice(entry.price()))
                    .build();
            gui.set(slot++, new GuiButton(icon, (clicker, clickType) -> buy(clicker, entry, displayName)));
        }
        return gui;
    }

    private void buy(Player player, ShopEntry entry, String displayName) {
        if (!economyService.withdraw(player.getUniqueId(), entry.price())) {
            messages.send(player, "economy.insufficient-funds");
            return;
        }
        java.util.Optional<ItemStack> stack = resolve(entry);
        if (stack.isEmpty()) {
            economyService.deposit(player.getUniqueId(), entry.price());
            messages.send(player, "economy.item-unavailable");
            return;
        }
        ItemStack purchased = stack.get();
        player.getInventory().addItem(purchased).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        messages.send(player, "economy.purchase-success", "item", displayName, "price", formatPrice(entry.price()));
    }

    /** Static label shown in the shop GUI/purchase message - avoids round-tripping the preview ItemStack's Component name into a String. */
    private String displayNameOf(ShopEntry entry) {
        if ("ACCESSORY".equalsIgnoreCase(entry.kind())) {
            return accessoryRepository.findById(entry.id()).map(AccessoryData::getName).orElse(entry.id());
        }
        if ("VANILLA".equalsIgnoreCase(entry.kind())) {
            return prettifyMaterialName(entry.id());
        }
        return itemManager.findById(entry.id()).map(WeaponData::getName).orElse(entry.id());
    }

    /** "DIAMOND_SWORD" -> "Diamond Sword" - fallback label for VANILLA entries, which have no configured display name. */
    private String prettifyMaterialName(String materialId) {
        String[] words = materialId.trim().toLowerCase().split("_");
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

    private String formatPrice(double price) {
        return price == Math.rint(price) ? String.valueOf((long) price) : String.valueOf(price);
    }

    private java.util.Optional<ItemStack> resolve(ShopEntry entry) {
        if ("ACCESSORY".equalsIgnoreCase(entry.kind())) {
            return accessoryRepository.findById(entry.id()).map(accessoryFactory::create);
        }
        if ("VANILLA".equalsIgnoreCase(entry.kind())) {
            try {
                return java.util.Optional.of(new ItemStack(Material.valueOf(entry.id().trim().toUpperCase())));
            } catch (IllegalArgumentException e) {
                return java.util.Optional.empty();
            }
        }
        return itemManager.createWeapon(entry.id());
    }
}
