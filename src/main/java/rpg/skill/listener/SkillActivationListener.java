package rpg.skill.listener;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import rpg.skill.service.SkillCastService;
import rpg.skill.service.SkillSocketService;

import java.util.List;
import java.util.Optional;

/**
 * Default skill keybind: pressing swap-hands (F) while holding a weapon casts the first
 * skill socketed into it. Cancels the vanilla item-swap so the offhand slot is left alone.
 * A slot-selection GUI can be layered on top of {@link SkillCastService} later without
 * changing this listener's contract.
 */
public final class SkillActivationListener implements Listener {

    private final SkillCastService castService;
    private final SkillSocketService socketService;

    public SkillActivationListener(SkillCastService castService, SkillSocketService socketService) {
        this.castService = castService;
        this.socketService = socketService;
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        List<String> socketed = socketService.getSocketedSkills(player.getInventory().getItemInMainHand());
        if (socketed.isEmpty()) {
            return;
        }
        event.setCancelled(true);

        Optional<SkillCastService.CastFailure> failure = castService.cast(player, socketed.get(0));
        failure.ifPresent(f -> player.sendMessage(ChatColor.RED + describe(f)));
    }

    private String describe(SkillCastService.CastFailure failure) {
        return switch (failure) {
            case UNKNOWN_SKILL, NO_EXECUTOR -> "このスキルは設定されていません。";
            case WRONG_WEAPON -> "この武器ではこのスキルを使用できません。";
            case NOT_SOCKETED -> "武器にスキルが装着されていません。";
            case NOT_LEARNED -> "このスキルをまだ習得していません。";
            case ON_COOLDOWN -> "スキルはクールタイム中です。";
            case NOT_ENOUGH_SP -> "SPが足りません。";
        };
    }
}
