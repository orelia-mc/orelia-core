package rpg.util;

import java.util.Locale;

/**
 * Formats a money amount into a compact {@code k}/{@code m}-suffixed string (e.g. {@code 1500}
 * -> {@code "1.5k"}, {@code 2000000} -> {@code "2m"}) for display in the GUI/scoreboard/chat/
 * placeholders. Values under 1000 are shown as-is (1 decimal place only if not a whole number).
 */
public final class MoneyFormat {

    private MoneyFormat() {
    }

    public static String format(double amount) {
        double abs = Math.abs(amount);
        String sign = amount < 0 ? "-" : "";
        if (abs >= 1_000_000) {
            return sign + trimTrailingZero(abs / 1_000_000) + "m";
        }
        if (abs >= 1_000) {
            return sign + trimTrailingZero(abs / 1_000) + "k";
        }
        return sign + trimTrailingZero(abs);
    }

    private static String trimTrailingZero(double value) {
        String formatted = String.format(Locale.ROOT, "%.1f", value);
        return formatted.endsWith(".0") ? formatted.substring(0, formatted.length() - 2) : formatted;
    }
}
