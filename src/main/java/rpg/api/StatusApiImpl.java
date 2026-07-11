package rpg.api;

import rpg.status.model.PlayerStatusComponent;
import rpg.status.service.StatusService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

final class StatusApiImpl implements StatusApi {

    private final StatusService statusService;

    StatusApiImpl(StatusService statusService) {
        this.statusService = statusService;
    }

    @Override
    public Optional<Integer> getLevel(UUID playerId) {
        return statusService.component(playerId).map(PlayerStatusComponent::getLevel);
    }

    @Override
    public Map<String, Double> getFinalStats(UUID playerId) {
        Map<String, Double> result = new HashMap<>();
        statusService.getFinalStats(playerId).ifPresent(sheet ->
                sheet.asMap().forEach((type, value) -> result.put(type.name(), value)));
        return result;
    }

    @Override
    public void addExperience(UUID playerId, long amount) {
        statusService.addExperience(playerId, amount);
    }

    @Override
    public boolean tryConsumeSp(UUID playerId, double amount) {
        return statusService.tryConsumeSp(playerId, amount);
    }

    @Override
    public void damage(UUID playerId, double amount) {
        statusService.damage(playerId, amount);
    }

    @Override
    public void heal(UUID playerId, double amount) {
        statusService.heal(playerId, amount);
    }

    @Override
    public List<LeaderboardEntry> getLeaderboard(int limit) {
        return statusService.getLeaderboard(limit).stream()
                .map(entry -> new LeaderboardEntry(entry.uuid(), entry.name(), entry.level(), entry.experience()))
                .collect(Collectors.toList());
    }
}
