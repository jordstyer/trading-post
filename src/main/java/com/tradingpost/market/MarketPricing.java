package com.tradingpost.market;

/**
 * The single source of pricing truth, as a pure function of numbers only (no Minecraft or config
 * types). Both sides use it identically: the server to charge/pay exactly, and the client to show
 * the player the exact projected total before they click. Because it's pure and both sides are fed
 * the same parameters (stock bounds sent in the packet, price factors sent in the packet header),
 * the client's projection always equals what the server will do - the server just re-runs it
 * against its own live stock and ignores whatever the client claimed.
 */
public final class MarketPricing {

    /** Result of a bulk quote: how many units actually trade, and the exact emerald total across them. */
    public record Quote(int filledQty, long total) {
    }

    private MarketPricing() {
    }

    /**
     * Exact (unrounded, unfloored) per-unit price at a given stock level - the true continuous
     * price curve. {@link #unitPrice} rounds this for display; {@link #quoteBuy}/{@link #quoteSell}
     * sum it directly and round only the grand total, which is what makes bulk pricing below 1
     * emerald per unit possible (e.g. "1 emerald per 16 logs" - basePrice = 1/16).
     *
     * Price moves opposite to stock, pivoting on baseStock (the colony's normal holding):
     *   - stock at reserve floor (minStock) -> price = maxPriceFactor x basePrice (scarce, dear)
     *   - stock at normal holding (baseStock) -> price = basePrice
     *   - stock at ceiling (maxStock) -> price = minPriceFactor x basePrice (glut, cheap)
     *
     * Linear on each side of baseStock, clamped into [minPriceFactor, maxPriceFactor].
     */
    public static double unitPriceExact(int minStock, int baseStock, int maxStock, double basePrice,
                                         int stock, double minPriceFactor, double maxPriceFactor) {
        double factor;
        if (stock <= baseStock) {
            // Below (or at) normal holding: interpolate maxPriceFactor (at floor) -> 1.0 (at base).
            int span = baseStock - minStock;
            double t = span <= 0 ? 1.0 : (double) (stock - minStock) / (double) span;
            factor = maxPriceFactor + (1.0 - maxPriceFactor) * t;
        } else {
            // Above normal holding: interpolate 1.0 (at base) -> minPriceFactor (at ceiling).
            int span = maxStock - baseStock;
            double t = span <= 0 ? 1.0 : (double) (stock - baseStock) / (double) span;
            factor = 1.0 + (minPriceFactor - 1.0) * t;
        }
        factor = Math.max(minPriceFactor, Math.min(maxPriceFactor, factor));
        return basePrice * factor;
    }

    /**
     * <p><b>Caution:</b> with the economy anchored at 1 emerald per 16 logs, real unit prices run
     * 0.0625 to 1.5, so this rounds nearly every rarity tier to an identical "1". It is not usable
     * as a rarity signal - the UI quotes a 64-unit lot instead (see {@code TradingPostScreen}).
     * Prefer {@link #unitPriceExact}, or a quote, for anything a player reads.
     *
     * Per-unit price in whole emeralds at a given stock level, for display purposes (the row list
     * and detail panel). Rounded to the nearest emerald and floored at 1 so nothing ever *displays*
     * as free - this is only an indicative label though, not the real charge: for a cheap bulk item
     * this is the price of buying exactly 1 (which really does cost a minimum of 1 emerald, same as
     * the total-order floor in {@link #quoteBuy}), while a larger order gets the true discounted
     * bulk rate from the continuous curve above.
     */
    public static int unitPrice(int minStock, int baseStock, int maxStock, double basePrice,
                                int stock, double minPriceFactor, double maxPriceFactor) {
        double exact = unitPriceExact(minStock, baseStock, maxStock, basePrice, stock, minPriceFactor, maxPriceFactor);
        return (int) Math.max(1, Math.round(exact));
    }

    /**
     * Cost to buy up to {@code requestedQty} units starting from {@code currentStock}. Buying draws
     * stock down toward the reserve floor; the request is capped at the available headroom
     * (currentStock - minStock). Each successive unit is priced against the falling stock via the
     * exact continuous curve, summed as a double and rounded only once at the end (a big order pays
     * more at the margin, but bulk buys of a cheap item can still total under 1 emerald per unit -
     * only the grand total is floored at 1, so any nonzero purchase costs something).
     */
    public static Quote quoteBuy(int minStock, int baseStock, int maxStock, double basePrice,
                                 int currentStock, int requestedQty,
                                 double minPriceFactor, double maxPriceFactor) {
        int capacity = Math.max(0, currentStock - minStock);
        int qty = Math.max(0, Math.min(requestedQty, capacity));
        double total = 0;
        int stock = currentStock;
        for (int i = 0; i < qty; i++) {
            // Price the unit as it leaves: at the stock level just before it's removed.
            total += unitPriceExact(minStock, baseStock, maxStock, basePrice, stock, minPriceFactor, maxPriceFactor);
            stock--;
        }
        long rounded = qty > 0 ? Math.max(1, Math.round(total)) : 0;
        return new Quote(qty, rounded);
    }

    /**
     * Earnings from selling up to {@code requestedQty} units starting from {@code currentStock}.
     * Selling pushes stock up toward the ceiling; the request is capped at the remaining room
     * (maxStock - currentStock). Each successive unit is priced against the rising stock via the
     * exact continuous curve, summed as a double and rounded only once at the end (a big dump earns
     * less at the margin; the grand total is floored at 1 for any nonzero sale).
     */
    public static Quote quoteSell(int minStock, int baseStock, int maxStock, double basePrice,
                                  int currentStock, int requestedQty,
                                  double minPriceFactor, double maxPriceFactor) {
        int capacity = Math.max(0, maxStock - currentStock);
        int qty = Math.max(0, Math.min(requestedQty, capacity));
        double total = 0;
        int stock = currentStock;
        for (int i = 0; i < qty; i++) {
            // Price the unit as it arrives: at the stock level just before it's added.
            total += unitPriceExact(minStock, baseStock, maxStock, basePrice, stock, minPriceFactor, maxPriceFactor);
            stock++;
        }
        long rounded = qty > 0 ? Math.max(1, Math.round(total)) : 0;
        return new Quote(qty, rounded);
    }
}
