package rpg.extra.api;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import rpg.core.config.ConfigFile;
import rpg.core.config.ConfigManager;
import rpg.extra.achievement.AchievementModule;
import rpg.extra.auction.AuctionModule;
import rpg.extra.guild.GuildModule;
import rpg.extra.housing.HousingModule;
import rpg.extra.housing.service.HousingService;
import rpg.extra.mail.MailModule;
import rpg.extra.mount.MountModule;
import rpg.extra.mount.service.MountService;
import rpg.extra.pet.PetModule;
import rpg.extra.pet.service.PetService;
import rpg.extra.ranking.RankingModule;
import rpg.extra.trade.TradeModule;
import rpg.extra.trade.model.TradeSession;
import rpg.gui.framework.GuiManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

final class ExtraDebugApiImpl implements ExtraDebugApi {

    private final ConfigManager configManager;
    private final AuctionModule auctionModule;
    private final MailModule mailModule;
    private final RankingModule rankingModule;
    private final PetModule petModule;
    private final MountModule mountModule;
    private final HousingModule housingModule;
    private final TradeModule tradeModule;
    private final AchievementModule achievementModule;
    private final GuildModule guildModule;
    private final GuiManager guiManager = new GuiManager();

    ExtraDebugApiImpl(ConfigManager configManager, AuctionModule auctionModule, MailModule mailModule,
                       RankingModule rankingModule, PetModule petModule, MountModule mountModule,
                       HousingModule housingModule, TradeModule tradeModule, AchievementModule achievementModule,
                       GuildModule guildModule) {
        this.configManager = configManager;
        this.auctionModule = auctionModule;
        this.mailModule = mailModule;
        this.rankingModule = rankingModule;
        this.petModule = petModule;
        this.mountModule = mountModule;
        this.housingModule = housingModule;
        this.tradeModule = tradeModule;
        this.achievementModule = achievementModule;
        this.guildModule = guildModule;
    }

    @Override
    public Set<String> listConfigFiles() {
        return configManager.getRegisteredFileNames();
    }

    @Override
    public Optional<String> getConfigValue(String fileName, String path) {
        ConfigFile file = tryGet(fileName);
        if (file == null || !file.get().contains(path)) {
            return Optional.empty();
        }
        return Optional.ofNullable(file.get().get(path)).map(String::valueOf);
    }

    @Override
    public boolean setConfigValue(String fileName, String path, String rawValue) {
        ConfigFile file = tryGet(fileName);
        if (file == null) {
            return false;
        }
        file.get().set(path, parseValue(rawValue));
        file.save();
        return true;
    }

    @Override
    public void saveConfig(String fileName) {
        ConfigFile file = tryGet(fileName);
        if (file != null) {
            file.save();
        }
    }

    @Override
    public List<String> describeConfigKeys(String fileName) {
        ConfigFile file = tryGet(fileName);
        if (file == null) {
            return List.of();
        }
        return file.get().getKeys(true).stream().sorted().toList();
    }

    @Override
    public List<ConfigTreeEntry> listConfigTree(String fileName) {
        ConfigFile file = tryGet(fileName);
        if (file == null) {
            return List.of();
        }
        List<ConfigTreeEntry> entries = new ArrayList<>();
        collectTree(file.get(), "", 0, entries);
        return entries;
    }

    private void collectTree(ConfigurationSection section, String pathPrefix, int depth, List<ConfigTreeEntry> out) {
        for (String key : section.getKeys(false)) {
            String path = pathPrefix.isEmpty() ? key : pathPrefix + "." + key;
            if (section.isConfigurationSection(key)) {
                out.add(new ConfigTreeEntry(path, depth, key, null, false));
                collectTree(section.getConfigurationSection(key), path, depth + 1, out);
            } else {
                out.add(new ConfigTreeEntry(path, depth, key, String.valueOf(section.get(key)), true));
            }
        }
    }

    @Override
    public void openAuction(Player player) {
        guiManager.open(player, auctionModule.getGuiScreen().build(player));
    }

    @Override
    public void openMail(Player player) {
        guiManager.open(player, mailModule.getGuiScreen().build(player));
    }

    @Override
    public void openRanking(Player player) {
        guiManager.open(player, rankingModule.getGuiScreen().build());
    }

    @Override
    public void openPet(Player player) {
        guiManager.open(player, petModule.getGuiScreen().build(player));
    }

    @Override
    public void openHouse(Player player) {
        guiManager.open(player, housingModule.getGuiScreen().build(player));
    }

    @Override
    public void openAchievement(Player player) {
        achievementModule.openGui(player);
    }

    @Override
    public void openGuild(Player player) {
        guildModule.getGuiManager().open(player, guildModule.getGuildGuiScreen().build(player));
    }

    @Override
    public Set<String> listPetIds() {
        return petModule.getPetService().getAllPets().keySet();
    }

    @Override
    public Set<String> getUnlockedPets(UUID playerId) {
        return petModule.getPetService().getUnlockedPets(playerId);
    }

    @Override
    public boolean forceUnlockPet(UUID playerId, String petId) {
        return petModule.getPetService().forceUnlock(playerId, petId) == PetService.ActionResult.OK;
    }

    @Override
    public Set<String> listMountIds() {
        return mountModule.getMountService().getAllMounts().keySet();
    }

    @Override
    public Set<String> getUnlockedMounts(UUID playerId) {
        return mountModule.getMountService().getUnlockedMounts(playerId);
    }

    @Override
    public boolean forceUnlockMount(UUID playerId, String mountId) {
        return mountModule.getMountService().forceUnlock(playerId, mountId) == MountService.ActionResult.OK;
    }

    @Override
    public Set<String> listHousePlotIds() {
        return housingModule.getPlotRepository().getAll().keySet();
    }

    @Override
    public Optional<String> getOwnedPlotId(UUID playerId) {
        return housingModule.getHousingService().getOwnedPlot(playerId).map(plot -> plot.getId());
    }

    @Override
    public boolean forceGrantPlot(Player player, String plotId) {
        return housingModule.getHousingService().forceGrant(player, plotId) == HousingService.ActionResult.OK;
    }

    @Override
    public boolean releasePlot(UUID playerId) {
        return housingModule.getHousingService().releasePlot(playerId);
    }

    @Override
    public Optional<UUID> getTradeCounterpart(UUID playerId) {
        return tradeModule.getTradeService().getSession(playerId).map(session -> session.getOtherPlayer(playerId));
    }

    @Override
    public boolean forceCancelTrade(UUID playerId) {
        Optional<TradeSession> session = tradeModule.getTradeService().getSession(playerId);
        tradeModule.getTradeService().forceCancelIfTrading(playerId);
        return session.isPresent();
    }

    private ConfigFile tryGet(String fileName) {
        try {
            return configManager.get(fileName);
        } catch (IllegalStateException e) {
            return null;
        }
    }

    private Object parseValue(String rawValue) {
        if ("true".equalsIgnoreCase(rawValue) || "false".equalsIgnoreCase(rawValue)) {
            return Boolean.parseBoolean(rawValue);
        }
        try {
            return Long.parseLong(rawValue);
        } catch (NumberFormatException ignored) {
        }
        try {
            return Double.parseDouble(rawValue);
        } catch (NumberFormatException ignored) {
        }
        return rawValue;
    }
}
