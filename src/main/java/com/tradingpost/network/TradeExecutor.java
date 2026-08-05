package com.tradingpost.network;

import com.tradingpost.advancement.ModAdvancements;
import com.tradingpost.config.TradingPostConfig;
import com.tradingpost.delivery.DeliveryService;
import com.tradingpost.market.Colony;
import com.tradingpost.market.MarketEntry;
import com.tradingpost.market.MarketPricing;
import com.tradingpost.market.MarketSavedData;
import com.tradingpost.menu.TradingPostMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Server-side handling for buy/sell requests. Never trusts the client: it re-derives the exact
 * progressive total from the live {@link MarketEntry} via {@link MarketPricing}, re-checks the
 * player's actual inventory, and only then mutates market state. The client's request is just an
 * "ask" - everything below runs from scratch against real server state.
 */
public final class TradeExecutor {

    /** A single click never trades more than this many units (bounds the pricing loop and one order's swing). */
    public static final int MAX_QUANTITY_PER_TRADE = 64 * 64;

    private TradeExecutor() {
    }

    public static void buy(ServerPlayer player, String colonyId, ResourceLocation itemId, int quantity) {
        MarketSavedData market = validateAndGetMarket(player);
        if (market == null) {
            return;
        }
        Colony colony = market.getColony(colonyId);
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        MarketEntry entry = (colony == null || item == null) ? null : colony.getEntry(item);
        if (entry == null) {
            return;
        }

        int requested = clampQuantity(quantity);
        MarketPricing.Quote quote = MarketPricing.quoteBuy(entry.getMinStock(), entry.getBaseStock(),
                entry.getMaxStock(), entry.getBasePrice(), entry.getCurrentStock(), requested,
                TradingPostConfig.MIN_PRICE_FACTOR.get(), TradingPostConfig.MAX_PRICE_FACTOR.get());
        if (quote.filledQty() <= 0) {
            return;
        }
        // Charge exactly the quoted total; if the player can't cover it we don't partial-fill
        // (the client disables the Buy button when unaffordable, so this is the anti-cheat path).
        if (quote.total() > Integer.MAX_VALUE || countItem(player, Items.EMERALD) < quote.total()) {
            return;
        }

        removeItem(player, Items.EMERALD, (int) quote.total());
        DeliveryService.deliverPurchase(player, item, quote.filledQty());
        entry.applyBuy(quote.filledQty());
        playRegisterSound(player, false);
        ModAdvancements.award(player, ModAdvancements.FIRST_ORDER);

        finish(player, market, colony);
    }

    public static void sell(ServerPlayer player, String colonyId, ResourceLocation itemId, int quantity) {
        MarketSavedData market = validateAndGetMarket(player);
        if (market == null) {
            return;
        }
        Colony colony = market.getColony(colonyId);
        Item item = ForgeRegistries.ITEMS.getValue(itemId);
        MarketEntry entry = (colony == null || item == null) ? null : colony.getEntry(item);
        if (entry == null) {
            return;
        }

        int requested = clampQuantity(quantity);
        // Cap the request to what the colony can still absorb, then to what the player actually has.
        int owned = countItem(player, item);
        MarketPricing.Quote quote = MarketPricing.quoteSell(entry.getMinStock(), entry.getBaseStock(),
                entry.getMaxStock(), entry.getBasePrice(), entry.getCurrentStock(), Math.min(requested, owned),
                TradingPostConfig.MIN_PRICE_FACTOR.get(), TradingPostConfig.MAX_PRICE_FACTOR.get());
        if (quote.filledQty() <= 0) {
            return;
        }

        removeItem(player, item, quote.filledQty());
        giveItem(player, Items.EMERALD, (int) Math.min(quote.total(), Integer.MAX_VALUE));
        entry.applySell(quote.filledQty());
        playRegisterSound(player, true);

        finish(player, market, colony);
    }

    /**
     * Cash-register "cha-ching" on a completed trade: a bell ding with a coin-like chime layered
     * over it. Vanilla has no register sound, so this is the closest pairing available.
     *
     * <p>Sent via {@code playNotifySound}, which targets only this player's connection. A
     * transaction confirmation is UI feedback about *your* order, so broadcasting it into the world
     * (as the previous villager-grunt did) would just be noise for anyone standing nearby.
     *
     * @param earning true when selling - pitched slightly brighter so money coming in and money
     *                going out don't sound identical.
     */
    private static void playRegisterSound(ServerPlayer player, boolean earning) {
        float pitch = earning ? 1.3f : 1.1f;
        player.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.5f, pitch);
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.35f, pitch + 0.2f);
    }

    /** The player must have this table's menu open (and still be within range of it) to trade. */
    private static MarketSavedData validateAndGetMarket(ServerPlayer player) {
        if (!(player.containerMenu instanceof TradingPostMenu menu) || !menu.stillValid(player)) {
            return null;
        }
        return MarketSavedData.get(player.getServer());
    }

    private static void finish(ServerPlayer player, MarketSavedData market, Colony colony) {
        market.setDirty();
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new S2CSyncMarketPacket(MarketNetworking.snapshot(colony)));
    }

    private static int clampQuantity(int quantity) {
        return Math.max(1, Math.min(quantity, MAX_QUANTITY_PER_TRADE));
    }

    private static int countItem(ServerPlayer player, Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void removeItem(ServerPlayer player, Item item, int amount) {
        int remaining = amount;
        for (ItemStack stack : player.getInventory().items) {
            if (remaining <= 0) {
                break;
            }
            if (stack.is(item)) {
                int take = Math.min(remaining, stack.getCount());
                stack.shrink(take);
                remaining -= take;
            }
        }
    }

    /**
     * Gives {@code amount} of an item, split into stacks and dropping anything that won't fit.
     * Public so {@link com.tradingpost.delivery.DeliveryService} can fall back to it when no landing
     * spot can be found for a delivery.
     */
    public static void giveItem(ServerPlayer player, Item item, int amount) {
        int remaining = amount;
        while (remaining > 0) {
            int give = Math.min(remaining, item.getMaxStackSize());
            ItemStack stack = new ItemStack(item, give);
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
            remaining -= give;
        }
    }
}
