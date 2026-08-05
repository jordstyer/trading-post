package com.tradingpost.market;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persistent, world-shared market state: every colony and every item they trade, with their
 * current supply. Stored as NBT on the overworld's {@link net.minecraft.world.level.DimensionDataStorage}
 * so it survives restarts and is the same for every player regardless of which table they use
 * or which dimension it's placed in.
 */
public class MarketSavedData extends SavedData {

    private static final String DATA_NAME = "trading_post_market";

    private final Map<String, Colony> colonies = new LinkedHashMap<>();

    private MarketSavedData() {
    }

    /** Fetches (or lazily creates and seeds) the single shared market for this server. */
    public static MarketSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        return overworld.getDataStorage().computeIfAbsent(MarketSavedData::load, MarketSavedData::createNew, DATA_NAME);
    }

    private static MarketSavedData createNew() {
        MarketSavedData data = new MarketSavedData();
        for (Colony colony : MarketDefaults.createDefaultColonies()) {
            data.colonies.put(colony.getId(), colony);
        }
        data.setDirty();
        return data;
    }

    public Collection<Colony> getColonies() {
        return colonies.values();
    }

    public Colony getColony(String id) {
        return colonies.get(id);
    }

    // --- NBT persistence -------------------------------------------------------------------

    /**
     * Layout on disk:
     * Colonies: [ { Id, DisplayName, Entries: [ { Item, BaseStock, CurrentStock, BasePrice } ] } ]
     *
     * baseStock/basePrice are re-seeded from MarketDefaults's shape (same colony/item set each
     * launch), but we still persist them so a future balance change to defaults doesn't silently
     * rewrite an existing world's baseline out from under currentStock/price expectations.
     */
    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag colonyList = new ListTag();
        for (Colony colony : colonies.values()) {
            CompoundTag colonyTag = new CompoundTag();
            colonyTag.putString("Id", colony.getId());
            colonyTag.putString("DisplayName", colony.getDisplayName());

            ListTag entryList = new ListTag();
            for (Map.Entry<Item, MarketEntry> e : colony.getEntries().entrySet()) {
                ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(e.getKey());
                if (itemId == null) {
                    continue;
                }
                MarketEntry entry = e.getValue();
                CompoundTag entryTag = new CompoundTag();
                entryTag.putString("Item", itemId.toString());
                entryTag.putInt("BaseStock", entry.getBaseStock());
                entryTag.putInt("CurrentStock", entry.getCurrentStock());
                entryTag.putDouble("BasePrice", entry.getBasePrice());
                entryList.add(entryTag);
            }
            colonyTag.put("Entries", entryList);
            colonyList.add(colonyTag);
        }
        tag.put("Colonies", colonyList);
        return tag;
    }

    /**
     * Rebuilds the market from NBT. Any item that no longer exists (e.g. removed by a datapack
     * or mod update) is silently skipped rather than crashing the load. If the tag has no
     * colonies at all (fresh/corrupt file), falls back to the default seed so the table never
     * opens onto an empty market.
     */
    private static MarketSavedData load(CompoundTag tag) {
        MarketSavedData data = new MarketSavedData();

        ListTag colonyList = tag.getList("Colonies", 10 /* CompoundTag id */);
        for (int i = 0; i < colonyList.size(); i++) {
            CompoundTag colonyTag = colonyList.getCompound(i);
            String id = colonyTag.getString("Id");
            String displayName = colonyTag.getString("DisplayName");
            Colony colony = new Colony(id, displayName);

            ListTag entryList = colonyTag.getList("Entries", 10);
            for (int j = 0; j < entryList.size(); j++) {
                CompoundTag entryTag = entryList.getCompound(j);
                ResourceLocation itemId = ResourceLocation.tryParse(entryTag.getString("Item"));
                Item item = itemId == null ? null : ForgeRegistries.ITEMS.getValue(itemId);
                if (item == null) {
                    continue;
                }
                int baseStock = entryTag.getInt("BaseStock");
                int currentStock = entryTag.getInt("CurrentStock");
                double basePrice = entryTag.getDouble("BasePrice");
                // Skip degenerate entries (e.g. from an older schema) - the merge below re-adds
                // them from defaults at a sane baseline rather than trading at a broken price.
                if (baseStock <= 0) {
                    continue;
                }
                colony.withEntry(item, new MarketEntry(baseStock, currentStock, basePrice));
            }
            data.colonies.put(id, colony);
        }

        mergeDefaults(data);
        return data;
    }

    /**
     * Adds any colony or item present in {@link MarketDefaults} but missing from the loaded market,
     * seeded at its baseline. This lets newly-added content appear in existing worlds without a
     * wipe, while leaving every already-traded stock/price exactly as it was saved.
     */
    private static void mergeDefaults(MarketSavedData data) {
        for (Colony def : MarketDefaults.createDefaultColonies()) {
            Colony existing = data.colonies.get(def.getId());
            if (existing == null) {
                data.colonies.put(def.getId(), def);
                continue;
            }
            for (Map.Entry<Item, MarketEntry> e : def.getEntries().entrySet()) {
                if (existing.getEntry(e.getKey()) == null) {
                    existing.withEntry(e.getKey(), e.getValue());
                }
            }
        }
    }
}
