package com.tradingpost.market;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads {@code data/trading_post/market_overrides/*.json} as a normal datapack directory - the
 * same mechanism vanilla uses for recipes/loot tables/advancements, so anyone who can write a
 * datapack can add items to the market or reprice existing ones without touching Java or
 * recompiling anything. Multiple files, and multiple datapacks, all merge together.
 *
 * <p>Each file looks like:
 * <pre>{@code
 * {
 *   "overrides": [
 *     { "colony": "woodcutters", "item": "minecraft:bamboo", "price": 0.0625, "stock": 2048 },
 *     { "colony": "miners_guild", "item": "examplemod:mithril_ingot", "price": 1.5, "stock": 64 }
 *   ]
 * }
 * }</pre>
 * {@code colony} must match one of the existing colony ids (woodcutters, desert_traders,
 * stonemasons, miners_guild, farmers_collective, ocean_traders). {@code price} and {@code stock}
 * are each optional when repricing an item the colony already sells - omit one to leave it as the
 * tag scan/curated list set it - but both are required when adding an item the colony doesn't sell
 * yet, since there's no existing value to fall back to.
 *
 * <p>Registered against {@code AddReloadListenerEvent} (see {@link ModDataListeners}), so this
 * reparses on every server start and every {@code /reload} - but see
 * {@link MarketDefaults#applyOverrides} for why an edited price only reaches items a world hasn't
 * seen yet, not ones already saved.
 */
public final class MarketOverrideManager extends SimpleJsonResourceReloadListener {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new Gson();

    // Must come after GSON above: static fields initialize in textual order, and the constructor
    // this triggers immediately reads GSON via super(GSON, ...) - declaring INSTANCE first left
    // GSON still null at that point, which crashed datapack loading with an NPE deep in vanilla's
    // GsonHelper (not obviously our bug from the stack trace alone).
    public static final MarketOverrideManager INSTANCE = new MarketOverrideManager();

    private volatile List<MarketOverride> overrides = List.of();

    private MarketOverrideManager() {
        super(GSON, "market_overrides");
    }

    public List<MarketOverride> getOverrides() {
        return overrides;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> data, ResourceManager resourceManager, ProfilerFiller profiler) {
        List<MarketOverride> parsed = new ArrayList<>();

        // Sorted rather than iterated in the map's own (hash) order, so that if two datapacks ever
        // define conflicting overrides for the same colony+item, which one wins is at least
        // deterministic and reproducible across runs, even though it's still not a resolved merge.
        data.keySet().stream().sorted().forEach(fileId -> {
            try {
                JsonObject root = data.get(fileId).getAsJsonObject();
                for (JsonElement element : GsonHelper.getAsJsonArray(root, "overrides")) {
                    JsonObject entry = element.getAsJsonObject();
                    String colony = GsonHelper.getAsString(entry, "colony");
                    ResourceLocation item = new ResourceLocation(GsonHelper.getAsString(entry, "item"));
                    Double price = entry.has("price") ? entry.get("price").getAsDouble() : null;
                    Integer stock = entry.has("stock") ? entry.get("stock").getAsInt() : null;
                    parsed.add(new MarketOverride(colony, item, price, stock));
                }
            } catch (RuntimeException e) {
                LOGGER.error("Couldn't parse market override file {}, skipping it", fileId, e);
            }
        });

        overrides = List.copyOf(parsed);
        LOGGER.info("Loaded {} market override(s) from {} file(s)", overrides.size(), data.size());
    }
}
