package com.tradingpost.advancement;

import com.tradingpost.TradingPostMod;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server-side awarding for the advancements that can't be expressed as a vanilla criterion.
 *
 * <p>"Place an order" and "receive an airdrop" aren't things vanilla can detect - there's no
 * trigger for them - so those advancements are declared with the {@code minecraft:impossible}
 * criterion in their JSON and granted from code at the moment the event actually happens. The
 * root advancement uses a real {@code inventory_changed} trigger and needs nothing here.
 */
public final class ModAdvancements {

    public static final ResourceLocation FIRST_ORDER =
            new ResourceLocation(TradingPostMod.MODID, "first_order");
    public static final ResourceLocation FIRST_DELIVERY =
            new ResourceLocation(TradingPostMod.MODID, "first_delivery");

    /** Criterion name; must match the key used in the advancement JSON. */
    private static final String CRITERION = "triggered";

    private ModAdvancements() {
    }

    /** Grants an advancement if the player doesn't already have it. No-op if it can't be found. */
    public static void award(ServerPlayer player, ResourceLocation id) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Advancement advancement = server.getAdvancements().getAdvancement(id);
        if (advancement != null) {
            player.getAdvancements().award(advancement, CRITERION);
        }
    }
}
