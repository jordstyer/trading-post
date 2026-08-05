package com.tradingpost.market;

import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.Tags;

import java.util.List;

/**
 * The background trade graph: which colony periodically buys from which other colony's catalog to
 * represent its own internal use (mine supports, feeding workers, scaffolding...). Pure data - see
 * {@link InterColonyTicker} for how a link actually moves stock.
 */
public final class InterColonyDemand {

    /** {@code buyerColonyId} periodically buys from {@code supplierColonyId}'s catalog. */
    public record DemandLink(String buyerColonyId, String supplierColonyId) {
    }

    private static final List<DemandLink> LINKS = List.of(
            new DemandLink("miners_guild", "woodcutters"),          // timber for mine supports
            new DemandLink("miners_guild", "farmers_collective"),   // feeding workers
            new DemandLink("stonemasons", "woodcutters"),           // scaffolding
            new DemandLink("stonemasons", "farmers_collective"),    // feeding workers
            new DemandLink("woodcutters", "farmers_collective"),    // feeding loggers
            new DemandLink("desert_traders", "farmers_collective"), // importing food - harsh climate
            new DemandLink("ocean_traders", "farmers_collective"),  // feeding sailors
            new DemandLink("farmers_collective", "miners_guild")    // metal fittings for farm equipment
    );

    /**
     * What a "massive project" bulk-buy is allowed to target - sturdy construction materials only.
     * Deliberately narrower than "any placeable block": flowers, saplings and crop blocks are all
     * technically placeable too, but nobody stockpiles hundreds of lilies to build a mine.
     */
    private static final List<TagKey<Item>> PROJECT_MATERIAL_TAGS = List.of(
            ItemTags.LOGS,
            ItemTags.PLANKS,
            Tags.Items.SAND,
            Tags.Items.SANDSTONE,
            Tags.Items.GLASS,
            Tags.Items.STONE,
            Tags.Items.COBBLESTONE,
            Tags.Items.GRAVEL,
            Tags.Items.OBSIDIAN,
            Tags.Items.STORAGE_BLOCKS
    );

    private InterColonyDemand() {
    }

    public static List<DemandLink> links() {
        return LINKS;
    }

    public static List<TagKey<Item>> projectMaterialTags() {
        return PROJECT_MATERIAL_TAGS;
    }
}
