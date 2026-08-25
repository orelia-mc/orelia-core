package rpg.extra.housing.service;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import rpg.core.config.ConfigFile;
import rpg.core.config.ConfigManager;
import rpg.extra.housing.model.HousePlot;
import rpg.extra.housing.repository.HousePlotRepository;

import java.util.Collection;
import java.util.Optional;

/**
 * Backs {@code /oladmin houseplot register|move|remove|list}: mutates the in-memory
 * {@link HousePlotRepository} and writes the placement fields (name/price/world/x/y/z/yaw)
 * back to {@code housing.yml} so the change survives a restart - same shape as
 * {@code rpg.npc.service.NpcAdminService}, the only other place in this codebase that writes
 * a Location into a {@code *.yml} file at runtime. Deliberately never touches anything beyond
 * {@code plots.<id>.*} - same narrow-scope discipline as NpcAdminService.
 */
public final class HousePlotAdminService {

    private static final String HOUSING_YML = "housing.yml";

    private final HousePlotRepository repository;
    private final HousingService housingService;
    private final ConfigManager configManager;

    public HousePlotAdminService(HousePlotRepository repository, HousingService housingService, ConfigManager configManager) {
        this.repository = repository;
        this.housingService = housingService;
        this.configManager = configManager;
    }

    /** Registers a brand-new plot at {@code location}. Empty if {@code id} is already taken. */
    public Optional<HousePlot> register(String id, double price, String name, Location location) {
        if (repository.findById(id).isPresent()) {
            return Optional.empty();
        }
        HousePlot plot = new HousePlot(id, name, price, location.getWorld().getName(),
                location.getX(), location.getY(), location.getZ(), location.getYaw());
        repository.add(id, plot);
        writePlacement(id, plot);
        return Optional.of(plot);
    }

    /** Moves an existing plot's definition to {@code location}. Returns false if {@code id} doesn't exist. */
    public boolean move(String id, Location location) {
        HousePlot existing = repository.findById(id).orElse(null);
        if (existing == null) {
            return false;
        }
        HousePlot moved = new HousePlot(id, existing.getName(), existing.getPrice(),
                location.getWorld().getName(), location.getX(), location.getY(), location.getZ(), location.getYaw());
        repository.replace(id, moved);
        writePlacement(id, moved);
        return true;
    }

    public enum RemoveResult { OK, NOT_FOUND, OWNED }

    /**
     * Deletes a plot's definition and its {@code housing.yml} section entirely.
     * {@link RemoveResult#OWNED} guards against deleting a plot a player has already paid for
     * and owns ({@code house_ownership} is a separate table this service deliberately never
     * touches - see {@link HousingService#isOwned} - orphaning a paying customer's home is worse
     * than a blocked command).
     */
    public RemoveResult remove(String id) {
        if (repository.findById(id).isEmpty()) {
            return RemoveResult.NOT_FOUND;
        }
        if (housingService.isOwned(id)) {
            return RemoveResult.OWNED;
        }
        repository.remove(id);
        ConfigFile file = configManager.get(HOUSING_YML);
        file.get().set("plots." + id, null);
        file.save();
        return RemoveResult.OK;
    }

    public Collection<HousePlot> list() {
        return repository.getAll().values();
    }

    private void writePlacement(String id, HousePlot plot) {
        ConfigFile file = configManager.get(HOUSING_YML);
        YamlConfiguration config = file.get();
        String base = "plots." + id + ".";
        config.set(base + "name", plot.getName());
        config.set(base + "price", plot.getPrice());
        config.set(base + "world", plot.getWorld());
        config.set(base + "x", plot.getX());
        config.set(base + "y", plot.getY());
        config.set(base + "z", plot.getZ());
        config.set(base + "yaw", (double) plot.getYaw());
        file.save();
    }
}
