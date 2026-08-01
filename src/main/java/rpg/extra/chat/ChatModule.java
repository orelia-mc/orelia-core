package rpg.extra.chat;

import rpg.core.OreliaPlugin;
import rpg.core.command.CommandAliasUtil;
import rpg.core.module.RpgModule;
import rpg.extra.chat.command.AdminChatCommand;
import rpg.extra.chat.command.ChatChannelCommand;
import rpg.extra.chat.command.MsgCommand;
import rpg.extra.chat.listener.ChatChannelListener;
import rpg.extra.chat.service.ChatChannelService;
import rpg.extra.guild.GuildModule;
import rpg.extra.party.PartyModule;

/**
 * Chat channel module: lets a player switch their default chat between public (left
 * completely untouched - orelia-serverutil's own ChatModule keeps formatting it exactly as
 * before this feature existed) / party / guild / admin via {@code /ol chat} (aliased to
 * {@code /chat}), plus one-off senders ({@code /oladmin chat}, {@code /ol party chat},
 * {@code /ol guild chat}) that broadcast without changing the sender's selected channel.
 * Registered right after {@link PartyModule}/{@link GuildModule} so both are already enabled
 * (not just registered) by the time this module's own {@code onEnable} runs.
 */
public final class ChatModule implements RpgModule {

    private ChatChannelService channelService;

    @Override
    public String getName() {
        return "chat";
    }

    @Override
    public void onEnable(OreliaPlugin plugin) {
        PartyModule partyModule = require(plugin, PartyModule.class);
        GuildModule guildModule = require(plugin, GuildModule.class);

        this.channelService = new ChatChannelService(partyModule.getPartyService(), guildModule.getGuildService());

        plugin.getServer().getPluginManager().registerEvents(
                new ChatChannelListener(channelService, partyModule.getPartyService(), guildModule.getGuildService(), plugin.getMessageManager()),
                plugin);

        ChatChannelCommand chatChannelCommand = new ChatChannelCommand(channelService, plugin.getMessageManager());
        String description = "チャットチャンネルを切り替えます。";
        String usage = "chat <public|party|guild|admin>";
        plugin.getPlayerCommandRegistry().register("chat", chatChannelCommand, description, usage);
        CommandAliasUtil.registerAlias(plugin, "chat", chatChannelCommand, description, "<public|party|guild|admin>");

        plugin.getAdminCommandRegistry().register("chat", new AdminChatCommand(plugin.getMessageManager()),
                "管理者チャットにメッセージを送信します。", "chat <message>");

        MsgCommand msgCommand = new MsgCommand(plugin.getMessageManager());
        String msgDescription = "指定したプレイヤーに個人メッセージを送信します。";
        String msgUsage = "msg <player> <message>";
        plugin.getPlayerCommandRegistry().register("msg", msgCommand, msgDescription, msgUsage);
        CommandAliasUtil.registerAlias(plugin, "msg", msgCommand, msgDescription, "<player> <message>");
    }

    @Override
    public void onDisable() {
    }

    public ChatChannelService getChannelService() {
        return channelService;
    }

    private <T extends RpgModule> T require(OreliaPlugin plugin, Class<T> type) {
        return plugin.getModuleManager().get(type)
                .orElseThrow(() -> new IllegalStateException("chat module requires " + type.getSimpleName()));
    }
}
