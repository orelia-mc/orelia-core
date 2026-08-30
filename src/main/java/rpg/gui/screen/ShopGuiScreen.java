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
import rpg.gui.framework.ConfirmGuiScreen;
import rpg.gui.framework.Gui;
import rpg.gui.framework.GuiButton;
import rpg.gui.framework.GuiManager;
import rpg.item.manager.ItemManager;
import rpg.item.model.WeaponData;
import rpg.relic.service.RelicShopService;
import rpg.util.ItemBuilder;
import rpg.util.MoneyFormat;

import java.util.List;

/**
 * NPC shop screen (SOW section 17 "NPCショップ") shared by the weapon/armor/accessory/relic
 * shop NPC types - which items are for sale is decided by the npc module, not here.
 */
public final class ShopGuiScreen {

    private final ItemManager itemManager;
    private final AccessoryRepository accessoryRepository;
    private final AccessoryFactory accessoryFactory;
    private final RelicShopService relicShopService;
    private final EconomyService economyService;
    private final GuiConfig guiConfig;
    private final MessageManager messages;
    private final GuiManager guiManager;

    public ShopGuiScreen(ItemManager itemManager, AccessoryRepository accessoryRepository,
                          AccessoryFactory accessoryFactory, RelicShopService relicShopService,
                          EconomyService economyService, GuiConfig guiConfig, MessageManager messages,
                          GuiManager guiManager) {
        this.itemManager = itemManager;
        this.accessoryRepository = accessoryRepository;
        this.accessoryFactory = accessoryFactory;
        this.relicShopService = relicShopService;
        this.economyService = economyService;
        this.guiConfig = guiConfig;
        this.messages = messages;
        this.guiManager = guiManager;
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
                    .name(displayNameComponentOf(entry, preview, displayName))
                    .lore("&%7価格: " + MoneyFormat.format(entry.price()))
                    .build();
            gui.set(slot++, new GuiButton(icon, (clicker, clickType) -> confirmPurchase(clicker, entry, displayName)));
        }
        return gui;
    }

    private void confirmPurchase(Player player, ShopEntry entry, String displayName) {
        String title = messages.raw("economy.confirm-purchase-title");
        List<String> description = List.of(messages.format("economy.confirm-purchase-line",
                "item", displayName, "price", MoneyFormat.format(entry.price())));
        Gui confirm = ConfirmGuiScreen.build(title, description,
                () -> buy(player, entry, displayName),
                () -> {
                });
        guiManager.open(player, confirm);
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
        messages.sendWithSound(player, "economy.purchase-success", GuiButton.DEFAULT_CLICK_SOUND,
                "item", displayName, "price", MoneyFormat.format(entry.price()));
    }

    /**
     * Plain-text label for chat messages (purchase confirmation/success) - never a raw id, and
     * never the bracketed "kind (id)" shape a debug/admin view might use. VANILLA falls back to
     * a prettified English material name here since chat text has no per-client localization the
     * way an item's own displayed name does (see {@link #displayNameComponentOf}) - a full
     * server-side Japanese material name table isn't worth building just for this one message
     * path.
     */
    private String displayNameOf(ShopEntry entry) {
        if ("ACCESSORY".equalsIgnoreCase(entry.kind())) {
            return accessoryRepository.findById(entry.id()).map(AccessoryData::getName).orElse(entry.id());
        }
        if ("RELIC".equalsIgnoreCase(entry.kind())) {
            return relicShopService.displayNameOf(entry.id()).orElse(entry.id());
        }
        if ("VANILLA".equalsIgnoreCase(entry.kind())) {
            return prettifyMaterialName(entry.id());
        }
        return itemManager.findById(entry.id()).map(WeaponData::getName).orElse(entry.id());
    }

    /**
     * The GUI icon's own displayed name (what a player actually reads when hovering the item in
     * their inventory) - for VANILLA entries this is a translatable component
     * ({@code item.minecraft.iron_chestplate}), which Minecraft's client resolves in the
     * viewing player's own game language automatically, rather than the English-only fallback
     * {@link #displayNameOf} falls back to for chat text.
     */
    private Component displayNameComponentOf(ShopEntry entry, ItemStack preview, String plainTextFallback) {
        if ("VANILLA".equalsIgnoreCase(entry.kind())) {
            return Component.translatable(preview.getType().translationKey());
        }
        return Component.text(plainTextFallback);
    }

    /** "DIAMOND_SWORD" -> "Diamond Sword" - chat-text-only fallback label for VANILLA entries; the GUI icon itself uses a translatable component instead, see {@link #displayNameComponentOf}. */
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

    private java.util.Optional<ItemStack> resolve(ShopEntry entry) {
        if ("ACCESSORY".equalsIgnoreCase(entry.kind())) {
            return accessoryRepository.findById(entry.id()).map(accessoryFactory::create);
        }
        if ("RELIC".equalsIgnoreCase(entry.kind())) {
            return relicShopService.build(entry.id());
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
