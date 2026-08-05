package com.tradingpost.client;

import com.tradingpost.TradingPostMod;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;

public final class ClientModelLayers {

    public static final ModelLayerLocation DELIVERY_DRONE =
            new ModelLayerLocation(new ResourceLocation(TradingPostMod.MODID, "delivery_drone"), "main");

    public static final ModelLayerLocation DELIVERY_PACKAGE =
            new ModelLayerLocation(new ResourceLocation(TradingPostMod.MODID, "delivery_package"), "main");

    private ClientModelLayers() {
    }
}
