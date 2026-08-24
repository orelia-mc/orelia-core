package rpg.world.api;

import org.bukkit.plugin.ServicePriority;
import rpg.dungeon.DungeonModule;
import rpg.npc.NpcModule;
import rpg.quest.QuestModule;
import rpg.core.OreliaPlugin;
import rpg.core.module.RpgModule;

/**
 * Publishes orelia-world's own cross-plugin API ({@link QuestApi}) to Bukkit's
 * {@code ServicesManager}, mirroring orelia-core's {@code ApiModule}. Registered last so
 * every module it wraps is already enabled.
 */
public final class WorldApiModule implements RpgModule {

    @Override
    public String getName() {
        return "world-api";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        QuestModule questModule = plugin.getModuleManager().get(QuestModule.class)
                .orElseThrow(() -> new IllegalStateException("world-api module requires quest module"));
        NpcModule npcModule = plugin.getModuleManager().get(NpcModule.class)
                .orElseThrow(() -> new IllegalStateException("world-api module requires npc module"));
        DungeonModule dungeonModule = plugin.getModuleManager().get(DungeonModule.class)
                .orElseThrow(() -> new IllegalStateException("world-api module requires dungeon module"));

        plugin.getServer().getServicesManager().register(
                QuestApi.class, new QuestApiImpl(plugin.getPlayerDataManager()), plugin, ServicePriority.Normal);
        plugin.getServer().getServicesManager().register(
                WorldDebugApi.class,
                new WorldDebugApiImpl(plugin.getConfigManager(), questModule.getProgressService(), questModule.getQuestGuiScreen(),
                        questModule.getQuestRepository(), npcModule.getRepository(), dungeonModule.getRepository(),
                        dungeonModule.getEncounterService(), dungeonModule.getGuiScreen(), plugin.getPlayerDataManager()),
                plugin, ServicePriority.Normal);
    }

    @Override
    public void onDisable() {
    }
}
