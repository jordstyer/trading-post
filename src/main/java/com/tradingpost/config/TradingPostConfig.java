package com.tradingpost.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Server-side tunables for the market. Registered as a COMMON config (see {@link com.tradingpost.TradingPostMod}).
 * Safe to read directly from {@code MarketEntry}/{@code MarketTicker}/{@code InterColonyTicker} because
 * those classes only ever run on the logical server - the client never evaluates pricing or regeneration itself.
 */
public final class TradingPostConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue REGEN_INTERVAL_TICKS;
    public static final ForgeConfigSpec.DoubleValue REGEN_FRACTION;
    public static final ForgeConfigSpec.DoubleValue MIN_PRICE_FACTOR;
    public static final ForgeConfigSpec.DoubleValue MAX_PRICE_FACTOR;
    public static final ForgeConfigSpec.DoubleValue RESERVE_FLOOR_FACTOR;
    public static final ForgeConfigSpec.DoubleValue MAX_STOCK_FACTOR;
    public static final ForgeConfigSpec.IntValue AI_TRADE_INTERVAL_TICKS;
    public static final ForgeConfigSpec.IntValue AI_TRADE_MIN_QUANTITY;
    public static final ForgeConfigSpec.IntValue AI_TRADE_MAX_QUANTITY;
    public static final ForgeConfigSpec.DoubleValue AI_PROJECT_CHANCE;
    public static final ForgeConfigSpec.IntValue AI_PROJECT_MIN_QUANTITY;
    public static final ForgeConfigSpec.IntValue AI_PROJECT_MAX_QUANTITY;
    public static final ForgeConfigSpec.IntValue DELIVERY_FLIGHT_MIN_ALTITUDE;
    public static final ForgeConfigSpec.IntValue DELIVERY_FLIGHT_TERRAIN_CLEARANCE;
    public static final ForgeConfigSpec.IntValue DELIVERY_FLIGHT_MAX_ALTITUDE;
    public static final ForgeConfigSpec.IntValue DELIVERY_FLIGHT_HALF_LENGTH;
    public static final ForgeConfigSpec.DoubleValue DELIVERY_FLIGHT_SPEED;
    public static final ForgeConfigSpec.IntValue DELIVERY_FALL_TICKS;
    public static final ForgeConfigSpec.IntValue DELIVERY_LANDING_SEARCH_RADIUS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.push("market");

        REGEN_INTERVAL_TICKS = builder
                .comment("How often (in server ticks) stock drifts back toward its normal holding. 20 ticks = 1 second.")
                .defineInRange("regenIntervalTicks", 100, 1, Integer.MAX_VALUE);

        REGEN_FRACTION = builder
                .comment("Fraction of the remaining stock gap closed on each regeneration tick.")
                .defineInRange("regenFraction", 0.05, 0.001, 1.0);

        MIN_PRICE_FACTOR = builder
                .comment("Price floor as a multiple of an item's base price (reached when stock is at the ceiling).")
                .defineInRange("minPriceFactor", 0.5, 0.01, 1.0);

        MAX_PRICE_FACTOR = builder
                .comment("Price ceiling as a multiple of an item's base price (reached when stock is at the reserve floor).")
                .defineInRange("maxPriceFactor", 2.0, 1.0, 100.0);

        RESERVE_FLOOR_FACTOR = builder
                .comment("Reserve floor a colony won't sell below, as a fraction of its normal holding. Buying is blocked here.")
                .defineInRange("reserveFloorFactor", 0.25, 0.0, 1.0);

        MAX_STOCK_FACTOR = builder
                .comment("Ceiling a colony will stockpile up to, as a multiple of its normal holding. Selling is blocked here.")
                .defineInRange("maxStockFactor", 2.0, 1.0, 100.0);

        builder.pop();

        builder.push("ai_trade");

        AI_TRADE_INTERVAL_TICKS = builder
                .comment("How often (in server ticks) colonies trade with each other in the background. 20 ticks = 1 second.")
                .defineInRange("aiTradeIntervalTicks", 200, 1, Integer.MAX_VALUE);

        AI_TRADE_MIN_QUANTITY = builder
                .comment("Smallest quantity a colony imports from another in one AI trade cycle.")
                .defineInRange("aiTradeMinQuantity", 4, 0, Integer.MAX_VALUE);

        AI_TRADE_MAX_QUANTITY = builder
                .comment("Largest quantity a colony imports from another in one AI trade cycle.")
                .defineInRange("aiTradeMaxQuantity", 16, 0, Integer.MAX_VALUE);

        AI_PROJECT_CHANCE = builder
                .comment("Chance, each AI trade cycle, that a colony also places a massive one-off bulk order for a "
                        + "building material - simulates a colony working on a big construction project.")
                .defineInRange("aiProjectChance", 0.08, 0.0, 1.0);

        AI_PROJECT_MIN_QUANTITY = builder
                .comment("Smallest quantity in a bulk project purchase.")
                .defineInRange("aiProjectMinQuantity", 128, 0, Integer.MAX_VALUE);

        AI_PROJECT_MAX_QUANTITY = builder
                .comment("Largest quantity in a bulk project purchase.")
                .defineInRange("aiProjectMaxQuantity", 512, 0, Integer.MAX_VALUE);

        builder.pop();

        builder.push("delivery");

        DELIVERY_FLIGHT_MIN_ALTITUDE = builder
                .comment("Minimum height (in blocks) above the landing spot the delivery plane will fly at, even "
                        + "over flat terrain.")
                .defineInRange("flightMinAltitude", 40, 8, 300);

        DELIVERY_FLIGHT_TERRAIN_CLEARANCE = builder
                .comment("Extra height (in blocks) the plane keeps above the highest terrain it detects along its "
                        + "chosen path - the actual flight altitude is whichever is taller: this clearance over the "
                        + "terrain, or flightMinAltitude.")
                .defineInRange("flightTerrainClearance", 20, 0, 100);

        DELIVERY_FLIGHT_MAX_ALTITUDE = builder
                .comment("Cap (in blocks above the landing spot) on how high the plane will climb to clear terrain, "
                        + "so one extreme peak can't send it into the stratosphere. Kept high enough to clear normal "
                        + "mountains - if it is set too low the plane will clip through them instead. Also clamped "
                        + "to the world height limit regardless of this value.")
                .defineInRange("flightMaxAltitude", 220, 20, 400);

        DELIVERY_FLIGHT_HALF_LENGTH = builder
                .comment("Distance (in blocks) from the point directly overhead the landing spot to where the plane "
                        + "enters and, after the drop, exits - so this is both the approach and the departure run. "
                        + "NOTE: this is automatically clamped to the server's simulation distance at spawn time. "
                        + "Entities outside that range are not ticked by the game, so a path longer than it would "
                        + "leave the plane frozen (and the delivery never arriving). Raising simulation distance in "
                        + "server.properties is what actually allows longer approaches.")
                .defineInRange("flightHalfLength", 160, 16, 512);

        DELIVERY_FLIGHT_SPEED = builder
                .comment("Plane speed in blocks per tick (0.5 = 10 blocks/second). Flight duration is derived from "
                        + "this and the path length, so changing the distance above keeps the same apparent speed "
                        + "instead of silently making the plane faster or slower.")
                .defineInRange("flightSpeed", 0.5, 0.05, 5.0);

        DELIVERY_FALL_TICKS = builder
                .comment("How long (in server ticks) the parachute package takes to fall from release altitude down "
                        + "to the ground after the plane drops it overhead.")
                .defineInRange("fallTicks", 200, 10, 1000);

        DELIVERY_LANDING_SEARCH_RADIUS = builder
                .comment("Radius (in blocks) searched around the player for a valid landing spot before falling "
                        + "back to giving the purchase directly to the player's inventory.")
                .defineInRange("landingSearchRadius", 4, 1, 16);

        builder.pop();

        SPEC = builder.build();
    }

    private TradingPostConfig() {
    }
}
