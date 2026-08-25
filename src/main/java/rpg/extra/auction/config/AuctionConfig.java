package rpg.extra.auction.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.time.Duration;

/**
 * Fee/listing-cap/duration/bidding settings for the auction house (SOW AuctionModule follow-up) -
 * previously all hardcoded (no fee at all, {@code AuctionCommand}'s own 3-day constant, no
 * per-seller cap).
 */
public final class AuctionConfig {

    private double feeRate = 0.05;
    private int maxListingsPerSeller = 10;
    private long defaultDurationMillis = Duration.ofHours(72).toMillis();
    private long expiryCheckPeriodTicks = 20L * 60;
    private double bidMinIncrementRate = 0.05;

    public void load(YamlConfiguration config) {
        feeRate = config.getDouble("auction.fee-rate", 0.05);
        maxListingsPerSeller = config.getInt("auction.max-listings-per-seller", 10);
        defaultDurationMillis = Duration.ofHours(config.getLong("auction.default-duration-hours", 72)).toMillis();
        expiryCheckPeriodTicks = config.getLong("auction.expiry-check-period-ticks", 20L * 60);
        bidMinIncrementRate = config.getDouble("auction.bid.min-increment-rate", 0.05);
    }

    public double getFeeRate() {
        return feeRate;
    }

    public int getMaxListingsPerSeller() {
        return maxListingsPerSeller;
    }

    public long getDefaultDurationMillis() {
        return defaultDurationMillis;
    }

    /** How often (in ticks) the sweep that closes expired listings and settles finished bid auctions runs. */
    public long getExpiryCheckPeriodTicks() {
        return expiryCheckPeriodTicks;
    }

    /** Fraction (0.05 = 5%) a new bid must exceed the current highest bid (or the starting price, if none yet) by. */
    public double getBidMinIncrementRate() {
        return bidMinIncrementRate;
    }
}
