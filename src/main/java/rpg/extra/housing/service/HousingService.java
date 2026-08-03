package rpg.extra.housing.service;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import rpg.extra.housing.model.HousePlot;
import rpg.extra.housing.repository.HouseOwnershipRepository;
import rpg.extra.housing.repository.HousePlotRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Purchase and teleport-home flow for house plots (SOW HousingModule). Money moves through
 * Vault's {@link Economy}.
 */
public final class HousingService {

    public enum ActionResult {
        OK, PLOT_NOT_FOUND, PLOT_TAKEN, ALREADY_OWN_A_HOUSE, INSUFFICIENT_FUNDS, NO_HOUSE, WORLD_NOT_FOUND
    }

    private final HousePlotRepository plotRepository;
    private final HouseOwnershipRepository ownershipRepository;
    private final Economy economy;
    private final Map<UUID, String> ownerToPlot = new ConcurrentHashMap<>();

    public HousingService(HousePlotRepository plotRepository, HouseOwnershipRepository ownershipRepository, Economy economy) {
        this.plotRepository = plotRepository;
        this.ownershipRepository = ownershipRepository;
        this.economy = economy;
    }

    public void loadAll() {
        ownerToPlot.clear();
        ownerToPlot.putAll(ownershipRepository.loadAll());
    }

    public ActionResult purchase(Player player, String plotId) {
        HousePlot plot = plotRepository.findById(plotId).orElse(null);
        if (plot == null) {
            return ActionResult.PLOT_NOT_FOUND;
        }
        if (ownerToPlot.containsKey(player.getUniqueId())) {
            return ActionResult.ALREADY_OWN_A_HOUSE;
        }
        if (ownerToPlot.containsValue(plotId)) {
            return ActionResult.PLOT_TAKEN;
        }
        if (!economy.has(player, plot.getPrice())) {
            return ActionResult.INSUFFICIENT_FUNDS;
        }
        economy.withdrawPlayer(player, plot.getPrice());
        ownerToPlot.put(player.getUniqueId(), plotId);
        ownershipRepository.save(player.getUniqueId(), plotId);
        return ActionResult.OK;
    }

    /** Debug helper: grants {@code plotId} to {@code player} without an economy check. */
    public ActionResult forceGrant(Player player, String plotId) {
        HousePlot plot = plotRepository.findById(plotId).orElse(null);
        if (plot == null) {
            return ActionResult.PLOT_NOT_FOUND;
        }
        if (ownerToPlot.containsKey(player.getUniqueId())) {
            return ActionResult.ALREADY_OWN_A_HOUSE;
        }
        if (ownerToPlot.containsValue(plotId)) {
            return ActionResult.PLOT_TAKEN;
        }
        ownerToPlot.put(player.getUniqueId(), plotId);
        ownershipRepository.save(player.getUniqueId(), plotId);
        return ActionResult.OK;
    }

    /** Debug helper: releases {@code ownerId}'s plot (if any) so it can be re-purchased for testing. Returns false if they owned none. */
    public boolean releasePlot(UUID ownerId) {
        if (ownerToPlot.remove(ownerId) == null) {
            return false;
        }
        ownershipRepository.delete(ownerId);
        return true;
    }

    public Optional<HousePlot> getOwnedPlot(UUID ownerId) {
        String plotId = ownerToPlot.get(ownerId);
        return plotId == null ? Optional.empty() : plotRepository.findById(plotId);
    }

    public ActionResult teleportHome(Player player) {
        HousePlot plot = getOwnedPlot(player.getUniqueId()).orElse(null);
        if (plot == null) {
            return ActionResult.NO_HOUSE;
        }
        World world = Bukkit.getWorld(plot.getWorld());
        if (world == null) {
            return ActionResult.WORLD_NOT_FOUND;
        }
        player.teleport(new Location(world, plot.getX(), plot.getY(), plot.getZ(), plot.getYaw(), 0));
        return ActionResult.OK;
    }

    public Map<String, HousePlot> getAvailablePlots() {
        Map<String, HousePlot> available = new LinkedHashMap<>(plotRepository.getAll());
        available.keySet().removeAll(ownerToPlot.values());
        return available;
    }
}
