package com.tradingpost.market;

import com.mojang.logging.LogUtils;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Declares which colonies exist and what they trade, as tag rules rather than a fixed item list -
 * see {@link CatalogScanner} for how these get turned into actual colonies. This is what makes the
 * market modpack-compatible: a mod that tags its ingot {@code forge:ingots} is picked up by Miners'
 * Guild automatically, at a stock/price derived from {@link Rarity}, with no per-item work here.
 *
 * Scope is deliberately narrow: raw resources, the one-tier base materials that gate further
 * crafting (planks, ingots, glass...), and crops/saplings/flowers. No tools, no deeper crafted
 * goods - those are end-products, not the resources a colony would actually deal in.
 *
 * A handful of vanilla items have no tag that fits anywhere in the Forge/vanilla convention set
 * (cactus, kelp, fish, clay, sponge, the prismarine family) - those stay as a small curated
 * supplement per colony, clearly separated from the tag-driven bulk below.
 */
public final class MarketDefaults {

    /**
     * How hard an item is to get, mapped to a normal holding (stock) and a base price.
     *
     * <p>Stock baselines and prices are both anchored off logs (ABUNDANT): stock is deliberately
     * modest per tier (the inter-colony regen ticker restocks over time - see
     * {@link com.tradingpost.market.MarketTicker} - rather than every colony sitting on a huge static
     * buffer), and price is set so 16 logs costs 1 emerald, with every other tier keeping the same
     * relative multiple of that per-unit rate it always had (COMMON = 2x, UNCOMMON = 4x, etc).
     */
    public enum Rarity {
        /** Trivially renewable/plentiful bulk blocks: dirt-tier stone, sand, logs, farmable crops. */
        ABUNDANT(32 * 64, 1.0 / 16.0),
        /** Common but requires some processing or a specific (common) biome: planks, glass, bricks. */
        COMMON(16 * 64, 2.0 / 16.0),
        /** A real ore/resource run, or a less common biome/structure: coal, redstone, dusts. */
        UNCOMMON(4 * 64, 4.0 / 16.0),
        /** Meaningfully harder to accumulate in bulk: smelted ingots, gems, monument loot. */
        RARE(64, 8.0 / 16.0),
        /** Slow or luck-gated even for a dedicated player: compressed storage blocks. */
        PRECIOUS(16, 16.0 / 16.0),
        /** Deliberately scarce: single-source drops (elder guardians) with no easy bulk farm. */
        SCARCE(8, 24.0 / 16.0);

        final int stock;
        final double price;

        Rarity(int stock, double price) {
            this.stock = stock;
            this.price = price;
        }
    }

    private static final Logger LOGGER = LogUtils.getLogger();

    private MarketDefaults() {
    }

    public static List<Colony> createDefaultColonies() {
        List<Colony> colonies = CatalogScanner.scan(List.of(
                woodcutters(), desertTraders(), stonemasons(), minersGuild(), farmersCollective(), oceanTraders()));
        applyOverrides(colonies);
        return colonies;
    }

    /**
     * Folds {@code data/trading_post/market_overrides/*.json} (see {@link MarketOverrideManager})
     * on top of the tag-scanned/curated catalog: this runs last, so a datapack override always wins
     * over whatever the scan found. Invalid entries (unknown colony, unknown item, or a brand-new
     * item missing price/stock) are logged and skipped rather than failing the whole catalog - one
     * bad line in someone's datapack shouldn't take down the market.
     *
     * <p>This only shapes what a colony's <em>default</em> entry looks like. It has no way to reach
     * back into an already-saved world: {@code MarketSavedData.mergeDefaults} only backfills items a
     * save doesn't have yet and never touches ones it does (see that class for why), and this method
     * runs upstream of that same call - so editing a price here changes what new items/new worlds
     * start at, never what a player's already-saved market is currently charging.
     */
    private static void applyOverrides(List<Colony> colonies) {
        Map<String, Colony> byId = colonies.stream().collect(Collectors.toMap(Colony::getId, c -> c));

        for (MarketOverride override : MarketOverrideManager.INSTANCE.getOverrides()) {
            Colony colony = byId.get(override.colonyId());
            if (colony == null) {
                LOGGER.warn("Market override references unknown colony '{}' - skipping {}",
                        override.colonyId(), override.itemId());
                continue;
            }
            Item item = ForgeRegistries.ITEMS.getValue(override.itemId());
            if (item == null || item == Items.AIR) {
                LOGGER.warn("Market override references unknown item '{}' in colony '{}' - skipping",
                        override.itemId(), override.colonyId());
                continue;
            }

            MarketEntry existing = colony.getEntry(item);
            if (existing == null) {
                if (override.price() == null || override.stock() == null) {
                    LOGGER.warn("Market override adds new item '{}' to '{}' but is missing price or "
                                    + "stock (both are required for a new item) - skipping",
                            override.itemId(), override.colonyId());
                    continue;
                }
                colony.withEntry(item, MarketEntry.atBaseline(
                        Math.max(1, override.stock()), Math.max(0.0001, override.price())));
            } else {
                int stock = override.stock() != null ? Math.max(1, override.stock()) : existing.getBaseStock();
                double price = override.price() != null ? Math.max(0.0001, override.price()) : existing.getBasePrice();
                colony.withEntry(item, MarketEntry.atBaseline(stock, price));
            }
        }
    }

    private static CatalogScanner.ColonyBlueprint woodcutters() {
        return new CatalogScanner.ColonyBlueprint("woodcutters", "Woodcutters", List.of(
                new CatalogScanner.CategoryRule(ItemTags.LOGS, Rarity.ABUNDANT),
                // 1 log crafts into 4 planks, so planks are stocked at 4x logs' baseline -
                // otherwise the shop would be selling more raw logs than the planks they produce.
                new CatalogScanner.CategoryRule(ItemTags.PLANKS, Rarity.ABUNDANT, 4.0),
                new CatalogScanner.CategoryRule(Tags.Items.RODS_WOODEN, Rarity.ABUNDANT),
                new CatalogScanner.CategoryRule(ItemTags.SAPLINGS, Rarity.COMMON)
        ), Map.of());
    }

    private static CatalogScanner.ColonyBlueprint desertTraders() {
        return new CatalogScanner.ColonyBlueprint("desert_traders", "Desert Traders", List.of(
                new CatalogScanner.CategoryRule(Tags.Items.SAND, Rarity.ABUNDANT),
                new CatalogScanner.CategoryRule(Tags.Items.SANDSTONE, Rarity.ABUNDANT),
                new CatalogScanner.CategoryRule(Tags.Items.GLASS, Rarity.COMMON)
        ), Map.of(Items.CACTUS, Rarity.COMMON));
    }

    private static CatalogScanner.ColonyBlueprint stonemasons() {
        return new CatalogScanner.ColonyBlueprint("stonemasons", "Stonemasons", List.of(
                new CatalogScanner.CategoryRule(Tags.Items.STONE, Rarity.ABUNDANT),
                new CatalogScanner.CategoryRule(Tags.Items.COBBLESTONE, Rarity.ABUNDANT),
                new CatalogScanner.CategoryRule(Tags.Items.GRAVEL, Rarity.ABUNDANT),
                new CatalogScanner.CategoryRule(Tags.Items.OBSIDIAN, Rarity.UNCOMMON)
        ), Map.of());
    }

    private static CatalogScanner.ColonyBlueprint minersGuild() {
        return new CatalogScanner.ColonyBlueprint("miners_guild", "Miners' Guild", List.of(
                new CatalogScanner.CategoryRule(Tags.Items.ORES, Rarity.UNCOMMON),
                new CatalogScanner.CategoryRule(Tags.Items.RAW_MATERIALS, Rarity.UNCOMMON),
                new CatalogScanner.CategoryRule(Tags.Items.DUSTS, Rarity.UNCOMMON),
                new CatalogScanner.CategoryRule(Tags.Items.NUGGETS, Rarity.UNCOMMON),
                new CatalogScanner.CategoryRule(Tags.Items.INGOTS, Rarity.RARE),
                new CatalogScanner.CategoryRule(Tags.Items.GEMS, Rarity.RARE),
                new CatalogScanner.CategoryRule(Tags.Items.STORAGE_BLOCKS, Rarity.PRECIOUS)
        ), Map.of());
    }

    private static CatalogScanner.ColonyBlueprint farmersCollective() {
        return new CatalogScanner.ColonyBlueprint("farmers_collective", "Farmers' Collective", List.of(
                new CatalogScanner.CategoryRule(Tags.Items.CROPS, Rarity.ABUNDANT),
                new CatalogScanner.CategoryRule(Tags.Items.SEEDS, Rarity.ABUNDANT),
                new CatalogScanner.CategoryRule(ItemTags.FLOWERS, Rarity.COMMON),
                new CatalogScanner.CategoryRule(Tags.Items.EGGS, Rarity.COMMON)
        ), Map.of());
    }

    /** No tag cluster fits well here - Forge doesn't have generic tags for fish/kelp/clay/prismarine, so this colony is purely curated. */
    private static CatalogScanner.ColonyBlueprint oceanTraders() {
        Map<Item, Rarity> curated = new LinkedHashMap<>();
        curated.put(Items.KELP, Rarity.ABUNDANT);
        curated.put(Items.DRIED_KELP, Rarity.COMMON);
        curated.put(Items.COD, Rarity.COMMON);
        curated.put(Items.SALMON, Rarity.COMMON);
        curated.put(Items.CLAY, Rarity.COMMON);
        curated.put(Items.CLAY_BALL, Rarity.COMMON);
        curated.put(Items.PRISMARINE, Rarity.UNCOMMON);
        curated.put(Items.PRISMARINE_BRICKS, Rarity.UNCOMMON);
        curated.put(Items.DARK_PRISMARINE, Rarity.UNCOMMON);
        curated.put(Items.SEA_LANTERN, Rarity.RARE);
        curated.put(Items.SPONGE, Rarity.SCARCE);
        return new CatalogScanner.ColonyBlueprint("ocean_traders", "Ocean Traders", List.of(), curated);
    }
}
