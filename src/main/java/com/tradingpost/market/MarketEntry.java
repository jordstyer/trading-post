package com.tradingpost.market;

import com.tradingpost.config.TradingPostConfig;

/**
 * One tradable line item within a {@link Colony}: how much of it the colony has on hand right now
 * (currentStock), its normal holding (baseStock), and the emerald price it trades at around that
 * holding. The reserve floor and ceiling are derived from {@link TradingPostConfig} rather than
 * stored, so retuning the config retroactively adjusts every world. This class only ever runs
 * server-side, so reading the server's own config here is safe and needs no client sync.
 *
 * All pricing math lives in {@link MarketPricing}; this class just feeds it live numbers.
 */
public class MarketEntry {

    private final int baseStock;
    private int currentStock;
    private final double basePrice;

    public MarketEntry(int baseStock, int currentStock, double basePrice) {
        this.baseStock = baseStock;
        this.basePrice = basePrice;
        this.currentStock = clamp(currentStock);
    }

    public static MarketEntry atBaseline(int baseStock, double basePrice) {
        return new MarketEntry(baseStock, baseStock, basePrice);
    }

    public int getBaseStock() {
        return baseStock;
    }

    public int getCurrentStock() {
        return currentStock;
    }

    public double getBasePrice() {
        return basePrice;
    }

    /** Reserve floor: the colony won't be bought below this. */
    public int getMinStock() {
        return (int) Math.round(baseStock * TradingPostConfig.RESERVE_FLOOR_FACTOR.get());
    }

    /** Ceiling: the colony won't be sold above this. */
    public int getMaxStock() {
        return (int) Math.round(baseStock * TradingPostConfig.MAX_STOCK_FACTOR.get());
    }

    private int clamp(int stock) {
        return Math.max(getMinStock(), Math.min(getMaxStock(), stock));
    }

    /** Current per-unit price in whole emeralds, via the shared pricing function. */
    public int getUnitPrice() {
        return MarketPricing.unitPrice(getMinStock(), baseStock, getMaxStock(), basePrice,
                currentStock, TradingPostConfig.MIN_PRICE_FACTOR.get(), TradingPostConfig.MAX_PRICE_FACTOR.get());
    }

    /** Buying draws the colony's stock down toward the reserve floor. */
    public void applyBuy(int quantity) {
        currentStock = Math.max(getMinStock(), currentStock - quantity);
    }

    /** Selling makes them stockpile more, up to the ceiling. */
    public void applySell(int quantity) {
        currentStock = Math.min(getMaxStock(), currentStock + quantity);
    }

    /**
     * Drifts currentStock back toward baseStock by a fixed fraction of the remaining gap. Called
     * periodically (throttled) from the server tick handler; moves at least 1 unit per call while a
     * gap remains so regeneration is always visible, never stalls on rounding.
     */
    public void regenerate() {
        int gap = baseStock - currentStock;
        if (gap == 0) {
            return;
        }
        int step = (int) Math.ceil(Math.abs(gap) * TradingPostConfig.REGEN_FRACTION.get());
        step = Math.max(1, step);
        if (gap > 0) {
            currentStock = Math.min(baseStock, currentStock + step);
        } else {
            currentStock = Math.max(baseStock, currentStock - step);
        }
    }

    public void setCurrentStock(int currentStock) {
        this.currentStock = clamp(currentStock);
    }
}
