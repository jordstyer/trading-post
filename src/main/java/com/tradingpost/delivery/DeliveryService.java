package com.tradingpost.delivery;

import com.tradingpost.block.DeliveryCrateBlock;
import com.tradingpost.blockentity.DeliveryCrateBlockEntity;
import com.tradingpost.config.TradingPostConfig;
import com.tradingpost.entity.DeliveryDroneEntity;
import com.tradingpost.network.TradeExecutor;
import com.mojang.logging.LogUtils;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Turns a completed purchase into a drone delivery: finds a landing spot near the player, splits
 * the quantity into crate-sized stacks, and spawns a {@link DeliveryDroneEntity} to fly a
 * straight-line pass over it and drop the payload. Falls back to giving the items directly
 * (today's pre-Phase-6 behavior) only when no landing spot can be found at all - e.g. the player
 * is over open water or the void.
 */
public final class DeliveryService {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Landing spots already promised to an in-flight delivery.
     *
     * <p>The landing spot is picked the instant a purchase is made, but the crate only physically
     * appears ten-plus seconds later when the parachute touches down. Without this, two purchases
     * in quick succession both scan a spot that is still empty air and both pick it - then the
     * second delivery overwrites the first crate. That overwrite was silent and lossy: replacing a
     * block with the *same* block means {@code DeliveryCrateBlock.onRemove}'s
     * {@code !state.is(newState.getBlock())} guard is false, so the contents are never dropped,
     * and {@code setPayload} then writes over the slots. Reserving the spot up-front is what stops
     * a routine "buy two things in a row" from eating the first order.
     *
     * <p>Entries are released on delivery (or on any failure path). A server restart mid-flight
     * clears the set, which is harmless - the worst case is the rare double-booking that
     * {@code claimLandingSpot} still guards against at touchdown.
     */
    private static final Set<BlockPos> RESERVED = ConcurrentHashMap.newKeySet();

    /**
     * Ceiling on crates per order, so a pathological item can't carpet the area in planes and
     * crates. With the catalog's 16- and 64-stack items and {@code MAX_QUANTITY_PER_TRADE} this is
     * never reached (a full 4096-unit order of a 64-stack item is 3 crates); it only bites for a
     * modded 1-stack item, where the remainder is handed to the player instead.
     */
    private static final int MAX_CRATES_PER_DELIVERY = 12;

    /** Range (blocks) that additional crates are scattered over - same vicinity, not shoulder to shoulder. */
    private static final int SCATTER_MIN = 5;
    private static final int SCATTER_MAX = 14;

    /** How far above/below the search origin a column is probed for ground. */
    private static final int COLUMN_SEARCH_HEIGHT = 6;

    /** Crates in one order stay at least this far apart, so none land touching. */
    private static final int MIN_CRATE_SEPARATION = 5;
    /** Re-rolls allowed while looking for a well-separated spot before settling for any valid one. */
    private static final int SCATTER_ATTEMPTS = 8;

    /** One crate's worth of goods and the spot it's bound for. */
    public record CrateLoad(BlockPos landing, List<ItemStack> items) {
    }

    private DeliveryService() {
    }

    /**
     * Picks a landing spot for crate {@code index}, scattered around the player and kept clear of
     * the spots already chosen for this order.
     *
     * <p>The first crate lands right by the player. Later ones search from a random point ringed
     * around them, because searching from the player every time returns the nearest free block each
     * round and packs the crates shoulder to shoulder. Random origins alone still let two crates
     * land touching by chance, so candidates within {@link #MIN_CRATE_SEPARATION} of an
     * already-claimed spot are rejected and re-rolled - except on the final attempt, where any
     * valid spot beats dropping the crate from the order entirely.
     */
    private static BlockPos pickScatteredSpot(ServerLevel level, BlockPos playerPos, int index,
                                               List<CrateLoad> chosen) {
        for (int attempt = 0; attempt < SCATTER_ATTEMPTS; attempt++) {
            BlockPos origin = playerPos;
            if (index > 0) {
                double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
                int dist = SCATTER_MIN + level.getRandom().nextInt(SCATTER_MAX - SCATTER_MIN + 1);
                origin = origin.offset((int) Math.round(Math.cos(angle) * dist), 0,
                        (int) Math.round(Math.sin(angle) * dist));
            }
            BlockPos candidate = findLandingSpot(level, origin);
            if (candidate == null) {
                continue;
            }
            boolean lastChance = attempt == SCATTER_ATTEMPTS - 1;
            if (index == 0 || lastChance || isWellSeparated(candidate, chosen)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean isWellSeparated(BlockPos candidate, List<CrateLoad> chosen) {
        for (CrateLoad load : chosen) {
            if (load.landing().distSqr(candidate) < (double) MIN_CRATE_SEPARATION * MIN_CRATE_SEPARATION) {
                return false;
            }
        }
        return true;
    }

    /** Frees a reserved spot. Safe to call for a position that was never reserved. */
    public static void releaseLandingSpot(BlockPos pos) {
        RESERVED.remove(pos);
    }

    /**
     * Last-line guard run at touchdown: if the promised spot somehow already holds a crate,
     * find a free neighbour rather than clobbering it. Returns null if nothing is free, in which
     * case the caller should drop the payload as items rather than destroy it.
     */
    public static BlockPos claimLandingSpot(ServerLevel level, BlockPos intended) {
        if (!(level.getBlockState(intended).getBlock() instanceof DeliveryCrateBlock)) {
            return intended;
        }
        for (int r = 1; r <= 3; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    BlockPos candidate = intended.offset(dx, 0, dz);
                    if (isValidLanding(level, candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Payment and market state (in {@link TradeExecutor#buy}) are already committed by the time
     * this runs, so the purchase itself is never at risk here - everything below only decides how
     * the delivery arrives.
     */
    public static void deliverPurchase(ServerPlayer player, Item item, int quantity) {
        ServerLevel level = player.serverLevel();

        // An order too big for one crate becomes several - the plane drops one per crate rather
        // than cramming everything into a single container.
        List<List<ItemStack>> crateLoads = buildCrateLoads(item, quantity);

        // One landing spot per crate. findLandingSpot skips anything already in RESERVED, so
        // claiming each spot as we go is what makes the next call return a *different* block.
        //
        // Searching from the player every time would return the nearest free block each round and
        // pack the crates shoulder to shoulder. Instead each crate after the first searches from a
        // random point ringed around the player, so they come down scattered across the area rather
        // than in a neat row.
        List<CrateLoad> loads = new ArrayList<>();
        for (int i = 0; i < crateLoads.size(); i++) {
            BlockPos spot = pickScatteredSpot(level, player.blockPosition(), i, loads);
            if (spot == null) {
                continue;
            }
            RESERVED.add(spot);
            loads.add(new CrateLoad(spot, crateLoads.get(i)));
        }

        if (loads.isEmpty()) {
            TradeExecutor.giveItem(player, item, quantity);
            return;
        }

        // Anything the crates don't carry goes straight to the player, so neither a cramped landing
        // area nor the MAX_CRATES_PER_DELIVERY cap can silently swallow part of a paid-for order.
        int airlifted = loads.stream()
                .flatMap(load -> load.items().stream())
                .mapToInt(ItemStack::getCount)
                .sum();
        if (airlifted < quantity) {
            TradeExecutor.giveItem(player, item, quantity - airlifted);
        }

        BlockPos landingPos = loads.get(0).landing();
        int halfLength = effectiveHalfLength(level);
        java.util.UUID buyerId = player.getUUID();

        // pickFlightPath samples terrain at up to (8 candidates x coarse pass + 1 fine pass) ~72
        // columns, and any column outside the loaded area calls the chunk generator's noise
        // sampler directly (see surfaceAt) - each call costs low-single-digit milliseconds, so the
        // full scan can run into the hundreds of milliseconds. That's fine off-thread but would be
        // a real tick stall (a delivery-shaped lag spike on every purchase) if run inline here, so
        // the scan runs on the background executor and only the actual entity spawn - which must
        // happen on the server thread - is scheduled back onto it.
        CompletableFuture
                .supplyAsync(() -> pickFlightPath(level, landingPos, halfLength), Util.backgroundExecutor())
                .thenAcceptAsync(choice -> {
                    // The server may have shut down while the scan was running; a stale level
                    // must never be touched. (The player disconnecting doesn't matter - the
                    // delivery is a world event by this point, not tied to their session.)
                    if (level.getServer().isStopped()) {
                        loads.forEach(l -> releaseLandingSpot(l.landing()));
                        return;
                    }
                    int flightTicks = (int) Math.ceil((2.0 * halfLength) / TradingPostConfig.DELIVERY_FLIGHT_SPEED.get());
                    DeliveryDroneEntity.spawn(level, landingPos, loads, choice.headingRadians, choice.altitude,
                            halfLength, flightTicks, TradingPostConfig.DELIVERY_FALL_TICKS.get(), buyerId);
                }, level.getServer())
                .exceptionally(t -> {
                    LOGGER.error("Delivery flight-path planning failed; giving the purchase directly instead", t);
                    loads.forEach(l -> releaseLandingSpot(l.landing()));
                    level.getServer().execute(() -> TradeExecutor.giveItem(player, item, quantity));
                    return null;
                });
    }

    /**
     * The configured approach/departure run, clamped so the whole flight path stays inside the
     * server's entity-ticking region.
     *
     * <p>This clamp is load-bearing, not just tidiness: Minecraft only ticks entities in chunks
     * within simulation distance of a player. A plane spawned beyond that would sit frozen at its
     * entry point and never reach the drop, so the purchase would silently never arrive. One
     * chunk of margin is kept because the boundary is evaluated per-chunk.
     */
    private static int effectiveHalfLength(ServerLevel level) {
        int configured = TradingPostConfig.DELIVERY_FLIGHT_HALF_LENGTH.get();
        int simulationChunks = level.getServer().getPlayerList().getSimulationDistance();
        int tickableBlocks = Math.max(1, simulationChunks - 1) * 16;
        return Math.max(16, Math.min(configured, tickableBlocks));
    }

    private record HeadingChoice(double headingRadians, int altitude) {
    }

    /** Coarse spacing (blocks) used when comparing candidate headings against each other. */
    private static final int COARSE_STEP = 24;
    /** Fine spacing (blocks) used on the winning heading, to catch narrow ridges and spires. */
    private static final int FINE_STEP = 4;
    /** Evenly spaced candidate headings, rotated by a random offset each delivery. */
    private static final int HEADING_CANDIDATES = 8;

    /**
     * Picks the flattest straight-line route through the landing spot, then the altitude needed to
     * clear it.
     *
     * <p>Headings are evenly spaced (with a random rotation) rather than independently random:
     * random draws can all happen to land on the same bad side, whereas even spacing guarantees
     * the flat direction is actually considered if one exists. A coarse pass ranks the candidates,
     * then a fine pass re-measures only the winner, so narrow peaks between coarse samples can't
     * sneak under the plane.
     */
    private static HeadingChoice pickFlightPath(ServerLevel level, BlockPos landingPos, int halfLength) {
        int minAltitude = TradingPostConfig.DELIVERY_FLIGHT_MIN_ALTITUDE.get();
        int clearance = TradingPostConfig.DELIVERY_FLIGHT_TERRAIN_CLEARANCE.get();
        int maxAltitude = TradingPostConfig.DELIVERY_FLIGHT_MAX_ALTITUDE.get();

        double spacing = (Math.PI * 2.0) / HEADING_CANDIDATES;
        double offset = level.getRandom().nextDouble() * spacing;
        double bestHeading = offset;
        int bestSurface = Integer.MAX_VALUE;

        for (int i = 0; i < HEADING_CANDIDATES; i++) {
            double heading = offset + i * spacing;
            int surface = highestTerrainAlong(level, landingPos, heading, halfLength, COARSE_STEP);
            if (surface < bestSurface) {
                bestSurface = surface;
                bestHeading = heading;
            }
        }

        int surface = highestTerrainAlong(level, landingPos, bestHeading, halfLength, FINE_STEP);
        int needed = (surface == Integer.MIN_VALUE)
                ? minAltitude
                : (surface - landingPos.getY()) + clearance;

        // Never plan a path above the world ceiling, regardless of what the config allows.
        int worldCeiling = level.getMaxBuildHeight() - landingPos.getY() - 8;
        int ceiling = Math.max(minAltitude, Math.min(maxAltitude, worldCeiling));
        return new HeadingChoice(bestHeading, Mth.clamp(needed, minAltitude, ceiling));
    }

    /** Highest terrain surface found along the path, sampling every {@code step} blocks. */
    private static int highestTerrainAlong(ServerLevel level, BlockPos landingPos, double headingRadians,
                                            int halfLength, int step) {
        double dirX = -Math.sin(headingRadians);
        double dirZ = Math.cos(headingRadians);
        int highest = Integer.MIN_VALUE;

        for (int dist = -halfLength; dist <= halfLength; dist += step) {
            int x = landingPos.getX() + (int) Math.round(dirX * dist);
            int z = landingPos.getZ() + (int) Math.round(dirZ * dist);
            highest = Math.max(highest, surfaceAt(level, x, z));
        }
        return highest;
    }

    /**
     * Terrain height at a column. Loaded chunks use the live heightmap so player builds count;
     * unloaded columns fall back to the chunk generator's noise estimate.
     *
     * <p>That fallback is the whole reason this works: most of a multi-hundred-block flight path
     * lies outside the loaded area at the moment a purchase happens, so simply skipping unloaded
     * columns (the previous behaviour) left the planner effectively blind and it would happily
     * route straight through a mountain. {@code getBaseHeight} reads the generator's noise without
     * generating or loading the chunk, so it's safe to call for far-off columns.
     */
    private static int surfaceAt(ServerLevel level, int x, int z) {
        if (level.hasChunkAt(x, z)) {
            return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        }
        ServerChunkCache chunkSource = level.getChunkSource();
        return chunkSource.getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG,
                level, chunkSource.randomState());
    }

    /**
     * Splits an order into crate-sized loads, every stack at its normal maximum size.
     *
     * <p>The original version packed anything larger than one crate into {@code SLOTS} deliberately
     * *oversized* stacks (32 stacks of planks became 27 stacks of 75). Nothing was lost on arrival,
     * but stacks above an item's real limit are fragile once a player starts moving them - slot and
     * quick-move clamping can silently truncate them - and it simply looked wrong. Overflowing into
     * additional crates is both safer and what a player expects to see: a bigger order means more
     * crates coming off the plane.
     */
    private static List<List<ItemStack>> buildCrateLoads(Item item, int quantity) {
        int maxStack = Math.max(1, item.getMaxStackSize());
        List<List<ItemStack>> loads = new ArrayList<>();
        List<ItemStack> current = new ArrayList<>();
        int remaining = quantity;

        while (remaining > 0 && loads.size() < MAX_CRATES_PER_DELIVERY) {
            current.add(new ItemStack(item, Math.min(maxStack, remaining)));
            remaining -= Math.min(maxStack, remaining);
            if (current.size() == DeliveryCrateBlockEntity.SLOTS || remaining <= 0) {
                loads.add(current);
                current = new ArrayList<>();
            }
        }
        return loads;
    }

    /**
     * Outward ring search around {@code origin} for a spot with solid ground below and open air at
     * the spot itself. Tries the configured radius first, then doubles it once before giving up.
     */
    private static BlockPos findLandingSpot(ServerLevel level, BlockPos origin) {
        int radius = TradingPostConfig.DELIVERY_LANDING_SEARCH_RADIUS.get();
        BlockPos spot = searchRing(level, origin, radius);
        if (spot != null) {
            return spot;
        }
        return searchRing(level, origin, radius * 2);
    }

    private static BlockPos searchRing(ServerLevel level, BlockPos origin, int radius) {
        for (int r = 0; r <= radius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    BlockPos found = findGroundAtColumn(level, origin.getX() + dx, origin.getY(), origin.getZ() + dz);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Finds a landable block in a column, searching outward from the player's own level rather
     * than scanning top-down, so the nearest-in-height match wins.
     *
     * <p>The band is deliberately wider than the original +1/-3: crates after the first are
     * scattered up to {@link #SCATTER_MAX} blocks away (see {@code deliverPurchase}), and on any
     * sloped ground a column that far out sits well outside a four-block window - which would
     * fail to place, quietly shrinking the airdrop and pushing goods into the player's inventory
     * instead.
     */
    private static BlockPos findGroundAtColumn(ServerLevel level, int x, int y, int z) {
        for (int step = 0; step <= COLUMN_SEARCH_HEIGHT; step++) {
            for (int dy : (step == 0 ? new int[]{0} : new int[]{-step, step})) {
                BlockPos landingPos = new BlockPos(x, y + dy, z);
                if (isValidLanding(level, landingPos)) {
                    return landingPos;
                }
            }
        }
        return null;
    }

    private static boolean isValidLanding(ServerLevel level, BlockPos pos) {
        // Skip spots already promised to a delivery that's still in the air (see RESERVED).
        if (RESERVED.contains(pos)) {
            return false;
        }
        BlockState here = level.getBlockState(pos);
        if (!here.getCollisionShape(level, pos).isEmpty() || !level.getFluidState(pos).isEmpty()) {
            return false;
        }
        BlockState below = level.getBlockState(pos.below());
        return below.isFaceSturdy(level, pos.below(), Direction.UP);
    }
}
