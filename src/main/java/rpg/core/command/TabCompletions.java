package rpg.core.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Shared prefix-filtering helper for the {@code /ol}/{@code /oladmin} family of commands. */
final class TabCompletions {

    private TabCompletions() {
    }

    static List<String> matching(Collection<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }
}
