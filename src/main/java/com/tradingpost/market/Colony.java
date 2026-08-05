package com.tradingpost.market;

import net.minecraft.world.item.Item;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A distant specialist colony trading a fixed set of items with the player.
 * Purely a data holder - no ticking or persistence logic lives here, see
 * {@link MarketSavedData} for storage and {@link MarketTicker} for regeneration.
 */
public class Colony {

    private final String id;
    private final String displayName;
    private final Map<Item, MarketEntry> entries = new LinkedHashMap<>();

    public Colony(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Colony withEntry(Item item, MarketEntry entry) {
        entries.put(item, entry);
        return this;
    }

    public MarketEntry getEntry(Item item) {
        return entries.get(item);
    }

    public Map<Item, MarketEntry> getEntries() {
        return entries;
    }

    public Collection<MarketEntry> allEntries() {
        return entries.values();
    }
}
