package rpg.economy.vault;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import rpg.economy.service.BankService;
import rpg.economy.service.EconomyService;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Adapts {@link EconomyService}/{@link BankService} to Vault's {@code Economy} interface so
 * third-party plugins (shops, other RPG add-ons) can read/modify Orelia balances - and named
 * banks - through Vault (SOW section 16: "Vault連携を考慮した設計"). A bank has exactly one
 * owner and no separate member list - see {@link BankService}'s own note on why, which stems
 * from a limit in Vault's {@code Economy} interface itself, not an Orelia-side scope cut.
 */
public final class OreliaVaultEconomy implements Economy {

    private final EconomyService economyService;
    private final BankService bankService;

    public OreliaVaultEconomy(EconomyService economyService, BankService bankService) {
        this.economyService = economyService;
        this.bankService = bankService;
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
        return true;
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

    /** Every bank method below fails this same way for a name with no matching row - a bank must be created via {@link #createBank(String, OfflinePlayer)} before any other bank call accepts it. */
    private EconomyResponse bankNotFound(String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "No bank named '" + name + "' exists");
    }

    @Override
    @Deprecated
    public EconomyResponse createBank(String name, String player) {
        return createBank(name, Bukkit.getOfflinePlayer(player));
    }

    @Override
    public EconomyResponse createBank(String name, OfflinePlayer player) {
        if (!bankService.create(name, player.getUniqueId())) {
            return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, "A bank named '" + name + "' already exists");
        }
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse deleteBank(String name) {
        if (!bankService.delete(name)) {
            return bankNotFound(name);
        }
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse bankBalance(String name) {
        return bankService.getBalance(name)
                .map(balance -> new EconomyResponse(0, balance, EconomyResponse.ResponseType.SUCCESS, null))
                .orElseGet(() -> bankNotFound(name));
    }

    @Override
    public EconomyResponse bankHas(String name, double amount) {
        Optional<Double> balance = bankService.getBalance(name);
        if (balance.isEmpty()) {
            return bankNotFound(name);
        }
        if (balance.get() < amount) {
            return new EconomyResponse(0, balance.get(), EconomyResponse.ResponseType.FAILURE, "Insufficient bank funds");
        }
        return new EconomyResponse(0, balance.get(), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse bankWithdraw(String name, double amount) {
        if (!bankService.exists(name)) {
            return bankNotFound(name);
        }
        if (!bankService.withdraw(name, amount)) {
            return new EconomyResponse(0, bankService.getBalance(name).orElse(0.0), EconomyResponse.ResponseType.FAILURE, "Insufficient bank funds");
        }
        return new EconomyResponse(amount, bankService.getBalance(name).orElse(0.0), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    public EconomyResponse bankDeposit(String name, double amount) {
        if (!bankService.deposit(name, amount)) {
            return bankNotFound(name);
        }
        return new EconomyResponse(amount, bankService.getBalance(name).orElse(0.0), EconomyResponse.ResponseType.SUCCESS, null);
    }

    @Override
    @Deprecated
    public EconomyResponse isBankOwner(String name, String playerName) {
        return isBankOwner(name, Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    public EconomyResponse isBankOwner(String name, OfflinePlayer player) {
        if (!bankService.exists(name)) {
            return bankNotFound(name);
        }
        return bankService.isOwner(name, player.getUniqueId())
                ? new EconomyResponse(0, 0, EconomyResponse.ResponseType.SUCCESS, null)
                : new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, player.getName() + " is not the owner of bank '" + name + "'");
    }

    @Override
    @Deprecated
    public EconomyResponse isBankMember(String name, String playerName) {
        return isBankMember(name, Bukkit.getOfflinePlayer(playerName));
    }

    @Override
    public EconomyResponse isBankMember(String name, OfflinePlayer player) {
        if (!bankService.exists(name)) {
            return bankNotFound(name);
        }
        return bankService.isMember(name, player.getUniqueId())
                ? new EconomyResponse(0, 0, EconomyResponse.ResponseType.SUCCESS, null)
                : new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, player.getName() + " is not a member of bank '" + name + "'");
    }

    @Override
    public List<String> getBanks() {
        return bankService.getAllNames();
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
