package rpg.extra.api;

import org.bukkit.plugin.ServicePriority;
import rpg.extra.achievement.AchievementModule;
import rpg.extra.auction.AuctionModule;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;
import rpg.extra.guild.GuildModule;
import rpg.extra.housing.HousingModule;
import rpg.extra.mail.MailModule;
import rpg.extra.mount.MountModule;
import rpg.extra.party.PartyModule;
import rpg.extra.pet.PetModule;
import rpg.extra.ranking.RankingModule;
import rpg.extra.trade.TradeModule;

/**
 * Publishes orelia-extra's own cross-plugin debug API ({@link ExtraDebugApi}) to Bukkit's
 * {@code ServicesManager}, mirroring orelia-core's {@code ApiModule} and orelia-world's
 * {@code WorldApiModule}. Registered last so every module it wraps is already enabled.
 */
public final class ExtraApiModule implements RpgModule {

    @Override
    public String getName() {
        return "extra-api";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        AuctionModule auctionModule = require(plugin, AuctionModule.class);
        MailModule mailModule = require(plugin, MailModule.class);
        RankingModule rankingModule = require(plugin, RankingModule.class);
        GuildModule guildModule = require(plugin, GuildModule.class);
        PartyModule partyModule = require(plugin, PartyModule.class);
        PetModule petModule = require(plugin, PetModule.class);
        MountModule mountModule = require(plugin, MountModule.class);
        HousingModule housingModule = require(plugin, HousingModule.class);
        TradeModule tradeModule = require(plugin, TradeModule.class);
        AchievementModule achievementModule = require(plugin, AchievementModule.class);

        plugin.getServer().getServicesManager().register(
                ExtraDebugApi.class,
                new ExtraDebugApiImpl(plugin.getConfigManager(), auctionModule, mailModule, rankingModule,
                        petModule, mountModule, housingModule, tradeModule, achievementModule, guildModule),
                plugin, ServicePriority.Normal);
        plugin.getServer().getServicesManager().register(
                GuildApi.class, new GuildApiImpl(guildModule.getGuildService()), plugin, ServicePriority.Normal);
        plugin.getServer().getServicesManager().register(
                PartyApi.class, new PartyApiImpl(partyModule.getPartyService()), plugin, ServicePriority.Normal);
        plugin.getServer().getServicesManager().register(
                AchievementApi.class, new AchievementApiImpl(achievementModule), plugin, ServicePriority.Normal);
    }

    @Override
    public void onDisable() {
    }

    private <T extends RpgModule> T require(OreliaPlugin plugin, Class<T> type) {
        return plugin.getModuleManager().get(type)
                .orElseThrow(() -> new IllegalStateException("extra-api module requires " + type.getSimpleName()));
    }
}
