package com.tradingpost.market;

import com.tradingpost.TradingPostMod;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers {@link MarketOverrideManager} as a datapack reload listener. {@code AddReloadListenerEvent}
 * fires on the Forge bus (the default for {@code @Mod.EventBusSubscriber}, unlike the mod bus most
 * of this mod's other registration uses), on every server start and every {@code /reload}.
 */
@Mod.EventBusSubscriber(modid = TradingPostMod.MODID)
public final class ModDataListeners {

    private ModDataListeners() {
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(MarketOverrideManager.INSTANCE);
    }
}
