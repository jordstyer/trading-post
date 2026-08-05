package com.tradingpost.market;

import com.tradingpost.TradingPostMod;
import com.tradingpost.config.TradingPostConfig;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Drives supply regeneration for the whole market. Runs on the Forge (game) event bus, throttled
 * so we're not walking every colony/entry 20 times a second - regeneration is a slow drift, not
 * something that needs tick-perfect precision.
 */
@Mod.EventBusSubscriber(modid = TradingPostMod.MODID)
public final class MarketTicker {

    private static int tickCounter = 0;

    private MarketTicker() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        tickCounter++;
        if (tickCounter < TradingPostConfig.REGEN_INTERVAL_TICKS.get()) {
            return;
        }
        tickCounter = 0;

        ServerLevel overworld = event.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return;
        }

        MarketSavedData market = MarketSavedData.get(event.getServer());
        boolean changed = false;
        for (Colony colony : market.getColonies()) {
            for (MarketEntry entry : colony.allEntries()) {
                int before = entry.getCurrentStock();
                entry.regenerate();
                if (entry.getCurrentStock() != before) {
                    changed = true;
                }
            }
        }
        if (changed) {
            market.setDirty();
        }
    }
}
