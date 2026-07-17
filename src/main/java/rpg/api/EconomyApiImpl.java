package rpg.api;

import rpg.economy.service.EconomyService;

import java.util.UUID;

final class EconomyApiImpl implements EconomyApi {

    private final EconomyService economyService;

    EconomyApiImpl(EconomyService economyService) {
        this.economyService = economyService;
    }

    @Override
    public double getBalance(UUID uuid) {
        return economyService.getBalance(uuid);
    }

    @Override
    public boolean has(UUID uuid, double amount) {
        return economyService.has(uuid, amount);
    }

    @Override
    public void deposit(UUID uuid, double amount) {
        economyService.deposit(uuid, amount);
    }

    @Override
    public boolean withdraw(UUID uuid, double amount) {
        return economyService.withdraw(uuid, amount);
    }

    @Override
    public void setBalance(UUID uuid, double amount) {
        economyService.setBalance(uuid, amount);
    }
}
