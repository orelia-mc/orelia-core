package rpg.economy.service;

import rpg.economy.repository.BankRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public bank API backing Vault's {@code Economy#createBank}/{@code bankDeposit}/... family
 * ({@link rpg.economy.vault.OreliaVaultEconomy} is a thin EconomyResponse-wrapping adapter over
 * this, same relationship {@link EconomyService} has to that class's player-balance methods).
 * Membership has no separate concept from ownership - see {@link #isMember} - since Vault's
 * {@code Economy} interface itself never exposes a way to add a non-owner member to a bank.
 */
public final class BankService {

    private final BankRepository repository;

    public BankService(BankRepository repository) {
        this.repository = repository;
    }

    public boolean exists(String name) {
        return repository.exists(name);
    }

    /** {@code false} if a bank with this exact name already exists. */
    public boolean create(String name, UUID owner) {
        return repository.create(name, owner);
    }

    /** {@code false} if no bank with this name exists. */
    public boolean delete(String name) {
        return repository.delete(name);
    }

    public Optional<Double> getBalance(String name) {
        return repository.getBalance(name);
    }

    public boolean has(String name, double amount) {
        return repository.getBalance(name).map(balance -> balance >= amount).orElse(false);
    }

    /** {@code false} if the bank doesn't exist or its balance is below {@code amount} - balance is left untouched either way. */
    public boolean withdraw(String name, double amount) {
        if (amount <= 0) {
            return repository.exists(name);
        }
        Optional<Double> balance = repository.getBalance(name);
        if (balance.isEmpty() || balance.get() < amount) {
            return false;
        }
        repository.setBalance(name, balance.get() - amount);
        return true;
    }

    /** {@code false} if the bank doesn't exist. */
    public boolean deposit(String name, double amount) {
        if (amount <= 0) {
            return repository.exists(name);
        }
        Optional<Double> balance = repository.getBalance(name);
        if (balance.isEmpty()) {
            return false;
        }
        repository.setBalance(name, balance.get() + amount);
        return true;
    }

    public boolean isOwner(String name, UUID player) {
        return repository.ownerOf(name).map(owner -> owner.equals(player)).orElse(false);
    }

    /** Vault's {@code Economy} interface has no way to add a member other than the owner, so membership is exactly ownership. */
    public boolean isMember(String name, UUID player) {
        return isOwner(name, player);
    }

    public List<String> getAllNames() {
        return repository.getAllNames();
    }
}
