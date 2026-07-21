package rpg.core.command;

import org.bukkit.Bukkit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Shared prefix-filtering helper for the {@code /ol}/{@code /oladmin} family of commands. */
public final class TabCompletions {

    private TabCompletions() {
    }

    public static List<String> matching(Collection<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }

    /** Online player names starting with {@code prefix} - the common "target a player" tab-completion case. */
    public static List<String> onlinePlayerNames(String prefix) {
        List<String> names = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach(player -> names.add(player.getName()));
        return matching(names, prefix);
    }
}
