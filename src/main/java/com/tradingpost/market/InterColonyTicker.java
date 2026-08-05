package com.tradingpost.market;

import com.tradingpost.TradingPostMod;
import com.tradingpost.config.TradingPostConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Drives the background economy: colonies periodically buy from each other (see
 * {@link InterColonyDemand}) to represent their own internal use, independent of anything the
 * player does. Reuses {@link MarketEntry#applyBuy(int)} exactly as a player purchase would - that
 * method already self-clamps to the reserve floor, and {@link MarketTicker}'s regen-toward-baseline
 * is what pulls stock back afterward, so this never needs its own capacity/recovery logic.
 *
 * On top of the regular small trades, each cycle has a small chance of also firing one "bulk
 * project" purchase - some colony stocking up hard on a construction material (see
 * {@link InterColonyDemand#projectMaterialTags()}), as if working a big building job. This is
 * independent of the demand graph - it picks any colony in the market with matching stock, not
 * just ones with a defined demand link, since it's not tied to a particular buyer/seller
 * relationship the way the regular trades are.
 */
@Mod.EventBusSubscriber(modid = TradingPostMod.MODID)
public final class InterColonyTicker {

    private static final Random RANDOM = new Random();

    private static int tickCounter = 0;

    private InterColonyTicker() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        tickCounter++;
        if (tickCounter < TradingPostConfig.AI_TRADE_INTERVAL_TICKS.get()) {
            return;
        }
        tickCounter = 0;

        ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        MarketSavedData market = MarketSavedData.get(event.getServer());
        boolean changed = false;

        for (InterColonyDemand.DemandLink link : InterColonyDemand.links()) {
            if (runRegularTrade(market, link)) {
                changed = true;
            }
        }

        if (RANDOM.nextDouble() < TradingPostConfig.AI_PROJECT_CHANCE.get() && runBulkProject(market)) {
            changed = true;
        }

        if (changed) {
            market.setDirty();
        }
    }

    private static boolean runRegularTrade(MarketSavedData market, InterColonyDemand.DemandLink link) {
        Colony supplier = market.getColony(link.supplierColonyId());
        if (supplier == null) {
            return false;
        }
        List<Item> items = List.copyOf(supplier.getEntries().keySet());
        if (items.isEmpty()) {
            return false;
        }

        Item chosen = items.get(RANDOM.nextInt(items.size()));
        MarketEntry entry = supplier.getEntry(chosen);
        int qty = randomQuantity(TradingPostConfig.AI_TRADE_MIN_QUANTITY.get(), TradingPostConfig.AI_TRADE_MAX_QUANTITY.get());

        int before = entry.getCurrentStock();
        entry.applyBuy(qty);
        return entry.getCurrentStock() != before;
    }

    /** Picks a random colony and, if it stocks any construction material, buys a large quantity of one. */
    private static boolean runBulkProject(MarketSavedData market) {
        List<Colony> colonies = List.copyOf(market.getColonies());
        if (colonies.isEmpty()) {
            return false;
        }
        Colony supplier = colonies.get(RANDOM.nextInt(colonies.size()));

        List<Item> materials = new ArrayList<>();
        for (Item item : supplier.getEntries().keySet()) {
            if (isProjectMaterial(item)) {
                materials.add(item);
            }
        }
        if (materials.isEmpty()) {
            return false;
        }

        Item chosen = materials.get(RANDOM.nextInt(materials.size()));
        MarketEntry entry = supplier.getEntry(chosen);
        int qty = randomQuantity(TradingPostConfig.AI_PROJECT_MIN_QUANTITY.get(), TradingPostConfig.AI_PROJECT_MAX_QUANTITY.get());

        int before = entry.getCurrentStock();
        entry.applyBuy(qty);
        return entry.getCurrentStock() != before;
    }

    private static boolean isProjectMaterial(Item item) {
        ItemStack sample = item.getDefaultInstance();
        for (TagKey<Item> tag : InterColonyDemand.projectMaterialTags()) {
            if (sample.is(tag)) {
                return true;
            }
        }
        return false;
    }

    private static int randomQuantity(int min, int max) {
        return min + (max > min ? RANDOM.nextInt(max - min + 1) : 0);
    }
}
