package rpg.economy.vault;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import rpg.economy.service.EconomyService;

import java.util.List;
import java.util.UUID;

/**
 * Adapts {@link EconomyService} to Vault's {@code Economy} interface so third-party
 * plugins (shops, other RPG add-ons) can read/modify Orelia balances through Vault
 * (SOW section 16: "Vault連携を考慮した設計"). Banks are not supported - Orelia only
 * models a personal balance.
 */
public final class OreliaVaultEconomy implements Economy {

    private final EconomyService economyService;

    public OreliaVaultEconomy(EconomyService economyService) {
        this.economyService = economyService;
    }

    @Deprecated
    private UUID uuidOf(String playerName) {
        return Bukkit.getOfflinePlayer(playerName).getUniqueId();
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public String getName() {
        return "Orelia";
    }

    @Override
    public boolean hasBankSupport() {
        return false;
    }

    @Override
    public int fractionalDigits() {
        return 2;
    }

    @Override
    public String format(double amount) {
        return String.format("%.2f %s", amount, amount == 1.0 ? currencyNameSingular() : currencyNamePlural());
    }

    @Override
    public String currencyNamePlural() {
        return "Gold";
    }

    @Override
    public String currencyNameSingular() {
        return "Gold";
    }

    @Override
    @Deprecated
    public boolean hasAccount(String playerName) {
        return true;
    }

    @Override
    public boolean hasAccount(OfflinePlayer player) {
        return true;
    }

    @Override
    @Deprecated
    public boolean hasAccount(String playerName, String worldName) {
        return hasAccount(playerName);
    }

    @Override
    public boolean hasAccount(OfflinePlayer player, String worldName) {
        return hasAccount(player);
    }

    @Override
    @Deprecated
    public double getBalance(String playerName) {
        return economyService.getBalance(uuidOf(playerName));
    }

    @Override
    public double getBalance(OfflinePlayer player) {
        return economyService.getBalance(player.getUniqueId());
    }

    @Override
    @Deprecated
    public double getBalance(String playerName, String world) {
        return getBalance(playerName);
    }

    @Override
    public double getBalance(OfflinePlayer player, String world) {
        return getBalance(player);
    }

    @Override
    @Deprecated
    public boolean has(String playerName, double amount) {
        return economyService.has(uuidOf(playerName), amount);
    }

    @Override
    public boolean has(OfflinePlayer player, double amount) {
        return economyService.has(player.getUniqueId(), amount);
    }

    @Override
    @Deprecated
    public boolean has(String playerName, String worldName, double amount) {
        return has(playerName, amount);
    }

    @Override
    public boolean has(OfflinePlayer player, String worldName, double amount) {
        return has(player, amount);
    }

    @Override
    @Deprecated
    public EconomyResponse withdrawPlayer(String playerName, double amount) {
        return withdraw(uuidOf(playerName), amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, double amount) {
        return withdraw(player.getUniqueId(), amount);
    }

    @Override
    @Deprecated
    public EconomyResponse withdrawPlayer(String playerName, String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse withdrawPlayer(OfflinePlayer player, String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    @Override
    @Deprecated
    public EconomyResponse depositPlayer(String playerName, double amount) {
        return deposit(uuidOf(playerName), amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, double amount) {
        return deposit(player.getUniqueId(), amount);
    }

    @Override
    @Deprecated
    public EconomyResponse depositPlayer(String playerName, String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    @Override
    public EconomyResponse depositPlayer(OfflinePlayer player, String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    private EconomyResponse withdraw(UUID uuid, double amount) {
        if (!economyService.withdraw(uuid, amount)) {
            return new EconomyResponse(0, economyService.getBalance(uuid), EconomyResponse.ResponseType.FAILURE, "Insufficient funds");
        }
        return new EconomyResponse(amount, economyService.getBalance(uuid), EconomyResponse.ResponseType.SUCCESS, null);
    }

    private EconomyResponse deposit(UUID uuid, double amount) {
        economyService.deposit(uuid, amount);
        return new EconomyResponse(amount, economyService.getBalance(uuid), EconomyResponse.ResponseType.SUCCESS, null);
    }

    private EconomyResponse unsupported() {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, "Orelia does not support bank accounts");
    }

    @Override
    @Deprecated
    public EconomyResponse createBank(String name, String player) {
        return unsupported();
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        return unsupported();
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        return unsupported();
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return unsupported();
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        return unsupported();
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        return unsupported();
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        return unsupported();
    }

    @Override
    @Deprecated
    public EconomyResponse isBankOwner(String name, String playerName) {
        return unsupported();
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        return unsupported();
    }

    @Override
    @Deprecated
    public EconomyResponse isBankMember(String name, String playerName) {
        return unsupported();
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        return unsupported();
    }

    @Override
    public List<String> getBanks() {
        return List.of();
    }

    @Override
    @Deprecated
    public boolean createPlayerAccount(String playerName) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player) {
        return true;
    }

    @Override
    @Deprecated
    public boolean createPlayerAccount(String playerName, String worldName) {
        return true;
    }

    @Override
    public boolean createPlayerAccount(OfflinePlayer player, String worldName) {
        return true;
    }
}
