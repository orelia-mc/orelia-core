package rpg.api;

import java.util.UUID;

/**
 * Cross-plugin surface over the economy module. orelia-world/orelia-extra/orelia-debug call
 * this instead of going through Vault when they need to inspect or mutate an Orelia balance
 * directly (e.g. debug tooling), keeping {@code rpg.economy} internals private to core.
 */
public interface EconomyApi {

    double getBalance(UUID uuid);

    boolean has(UUID uuid, double amount);

    void deposit(UUID uuid, double amount);

    boolean withdraw(UUID uuid, double amount);

    void setBalance(UUID uuid, double amount);
}
