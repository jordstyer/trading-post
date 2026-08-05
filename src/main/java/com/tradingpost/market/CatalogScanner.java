package com.tradingpost.market;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The mechanical half of the tag-driven catalog (see {@link MarketDefaults} for the declarative
 * half - which tags mean which colony). Scans the live item registry once and sorts every item
 * into a colony by tag, which is what makes a modpack's items "just show up" without anyone
 * hand-listing them: as long as a mod tags its ingot {@code forge:ingots}, it's picked up here
 * exactly like a vanilla one.
 */
public final class CatalogScanner {

    /**
     * One tag a colony trades under, the baseline rarity items matching it start at, and an
     * optional stock multiplier on top of that rarity's baseline - for categories whose "normal
     * holding" should track a crafting relationship rather than the raw rarity alone (e.g. planks
     * stocked at 4x logs, since one log crafts into four planks).
     */
    public record CategoryRule(TagKey<Item> tag, MarketDefaults.Rarity rarity, double stockMultiplier) {
        public CategoryRule(TagKey<Item> tag, MarketDefaults.Rarity rarity) {
            this(tag, rarity, 1.0);
        }
    }

    /**
     * A colony's shape before scanning: id/display name, its ordered tag rules, and any items with
     * no fitting tag anywhere in the ecosystem (a handful of vanilla items like cactus or sponge -
     * see {@link MarketDefaults} for why these can't just be tag-matched).
     */
    public record ColonyBlueprint(String id, String displayName, List<CategoryRule> categories,
                                   Map<Item, MarketDefaults.Rarity> curatedExtras) {
    }

    private CatalogScanner() {
    }

    /**
     * Builds every colony by scanning {@link ForgeRegistries#ITEMS} once. An item is claimed by the
     * first blueprint (in list order) whose category list it matches, using that category's rarity;
     * an item matching no category anywhere is simply not sold, unless a blueprint's curatedExtras
     * names it directly (curated entries never overwrite a tag match, so ordering there doesn't matter).
     */
    public static List<Colony> scan(List<ColonyBlueprint> blueprints) {
        List<Colony> colonies = new ArrayList<>(blueprints.size());
        for (ColonyBlueprint blueprint : blueprints) {
            colonies.add(new Colony(blueprint.id(), blueprint.displayName()));
        }

        for (Item item : ForgeRegistries.ITEMS) {
            ItemStack sample = item.getDefaultInstance();
            for (int i = 0; i < blueprints.size(); i++) {
                CategoryRule matched = matchCategory(sample, blueprints.get(i));
                if (matched != null) {
                    colonies.get(i).withEntry(item, entryFor(sample, matched.rarity(), matched.stockMultiplier()));
                    break;
                }
            }
        }

        for (int i = 0; i < blueprints.size(); i++) {
            Colony colony = colonies.get(i);
            for (Map.Entry<Item, MarketDefaults.Rarity> extra : blueprints.get(i).curatedExtras().entrySet()) {
                if (colony.getEntry(extra.getKey()) == null) {
                    colony.withEntry(extra.getKey(), entryFor(extra.getKey().getDefaultInstance(), extra.getValue(), 1.0));
                }
            }
        }

        return colonies;
    }

    private static CategoryRule matchCategory(ItemStack sample, ColonyBlueprint blueprint) {
        for (CategoryRule rule : blueprint.categories()) {
            if (sample.is(rule.tag())) {
                return rule;
            }
        }
        return null;
    }

    /**
     * The category's baseline rarity, nudged up a tier for each step above COMMON in the item's own
     * declared vanilla rarity (the enum vanilla uses to color item names). A modded item that
     * declares itself RARE or EPIC ends up scarcer and pricier automatically, with zero per-item
     * curation - vanilla's Rarity ordinals (COMMON=0, UNCOMMON=1, RARE=2, EPIC=3) map directly onto
     * how many tiers to climb, clamped at the top of our own tier list. {@code stockMultiplier}
     * (see {@link CategoryRule}) is applied after that resolution, on top of whichever tier the
     * item lands on.
     */
    private static MarketEntry entryFor(ItemStack sample, MarketDefaults.Rarity baseRarity, double stockMultiplier) {
        int climb = sample.getRarity().ordinal();
        MarketDefaults.Rarity[] tiers = MarketDefaults.Rarity.values();
        MarketDefaults.Rarity resolved = tiers[Math.min(tiers.length - 1, baseRarity.ordinal() + climb)];
        int stock = Math.max(1, (int) Math.round(resolved.stock * stockMultiplier));
        return MarketEntry.atBaseline(stock, resolved.price);
    }
}
