package com.tradingpost.market;

import net.minecraft.resources.ResourceLocation;

/**
 * One entry from a {@code data/trading_post/market_overrides/*.json} file: add or reprice an item
 * within an existing colony. {@code price}/{@code stock} are boxed so a datapack can omit either
 * one to mean "leave whatever the tag scan/curated list already set" - only meaningful when the
 * item already exists in that colony; both are required to introduce a brand new item, since
 * there's nothing to fall back to. See {@link MarketOverrideManager} for parsing and
 * {@link MarketDefaults#applyOverrides} for how these get folded into the catalog.
 */
public record MarketOverride(String colonyId, ResourceLocation itemId, Double price, Integer stock) {
}
