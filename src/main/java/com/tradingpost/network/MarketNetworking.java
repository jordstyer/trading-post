package com.tradingpost.network;

import com.tradingpost.config.TradingPostConfig;
import com.tradingpost.market.Colony;
import com.tradingpost.market.MarketEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Shared wire format for sending market data to the client.
 *
 * The client is sent the raw stock bounds and the two global price-band factors so it can draw
 * stock bars and, crucially, compute the exact progressive buy/sell quote the player sees before
 * they commit - using the same {@link com.tradingpost.market.MarketPricing} the server uses. This is
 * a deliberate, narrow relaxation of "never send a formula to the client": the parameters aren't
 * secret, and the server still re-runs the pricing against its own live stock on execution and
 * ignores anything the client claims. The factors travel once per payload in {@link Market}.
 */
public final class MarketNetworking {

    private MarketNetworking() {
    }

    /** One item line. Stock bounds + base price let the client price any quantity locally for display. */
    public record EntrySnapshot(ResourceLocation itemId, Item item, int minStock, int baseStock,
                                int maxStock, int currentStock, double basePrice) {
    }

    /** One colony and everything it trades, as displayed. */
    public record ColonySnapshot(String id, String displayName, List<EntrySnapshot> entries) {
    }

    /** A whole market payload: the global price-band factors plus every colony. */
    public record Market(double minPriceFactor, double maxPriceFactor, List<ColonySnapshot> colonies) {
    }

    public static EntrySnapshot snapshot(Item item, MarketEntry entry) {
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(item);
        return new EntrySnapshot(itemId, item, entry.getMinStock(), entry.getBaseStock(),
                entry.getMaxStock(), entry.getCurrentStock(), entry.getBasePrice());
    }

    public static ColonySnapshot snapshot(Colony colony) {
        List<EntrySnapshot> entries = new ArrayList<>();
        for (Map.Entry<Item, MarketEntry> e : colony.getEntries().entrySet()) {
            if (ForgeRegistries.ITEMS.getKey(e.getKey()) == null) {
                continue;
            }
            entries.add(snapshot(e.getKey(), e.getValue()));
        }
        return new ColonySnapshot(colony.getId(), colony.getDisplayName(), entries);
    }

    // --- price-band factors (global, server-authoritative) ---------------------------------

    public static double minPriceFactor() {
        return TradingPostConfig.MIN_PRICE_FACTOR.get();
    }

    public static double maxPriceFactor() {
        return TradingPostConfig.MAX_PRICE_FACTOR.get();
    }

    // --- full market (sent when the menu opens) --------------------------------------------

    public static void writeMarket(FriendlyByteBuf buf, Collection<Colony> colonies) {
        buf.writeDouble(minPriceFactor());
        buf.writeDouble(maxPriceFactor());
        buf.writeVarInt(colonies.size());
        for (Colony colony : colonies) {
            writeColonySnapshot(buf, snapshot(colony));
        }
    }

    public static Market readMarket(FriendlyByteBuf buf) {
        double minFactor = buf.readDouble();
        double maxFactor = buf.readDouble();
        int count = buf.readVarInt();
        List<ColonySnapshot> colonies = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            colonies.add(readColonySnapshot(buf));
        }
        return new Market(minFactor, maxFactor, colonies);
    }

    // --- single colony (sent after a trade) ------------------------------------------------

    public static void writeColonySnapshot(FriendlyByteBuf buf, ColonySnapshot snapshot) {
        buf.writeUtf(snapshot.id());
        buf.writeUtf(snapshot.displayName());
        buf.writeVarInt(snapshot.entries().size());
        for (EntrySnapshot entry : snapshot.entries()) {
            buf.writeResourceLocation(entry.itemId());
            buf.writeVarInt(entry.minStock());
            buf.writeVarInt(entry.baseStock());
            buf.writeVarInt(entry.maxStock());
            buf.writeVarInt(entry.currentStock());
            buf.writeDouble(entry.basePrice());
        }
    }

    public static ColonySnapshot readColonySnapshot(FriendlyByteBuf buf) {
        String id = buf.readUtf();
        String displayName = buf.readUtf();
        int entryCount = buf.readVarInt();
        List<EntrySnapshot> entries = new ArrayList<>(entryCount);
        for (int i = 0; i < entryCount; i++) {
            ResourceLocation itemId = buf.readResourceLocation();
            int minStock = buf.readVarInt();
            int baseStock = buf.readVarInt();
            int maxStock = buf.readVarInt();
            int currentStock = buf.readVarInt();
            double basePrice = buf.readDouble();
            Item item = ForgeRegistries.ITEMS.getValue(itemId);
            entries.add(new EntrySnapshot(itemId, item, minStock, baseStock, maxStock, currentStock, basePrice));
        }
        return new ColonySnapshot(id, displayName, entries);
    }
}
