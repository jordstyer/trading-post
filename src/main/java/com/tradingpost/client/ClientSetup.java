package com.tradingpost.client;

import com.tradingpost.TradingPostMod;
import com.tradingpost.registry.ModEntities;
import com.tradingpost.registry.ModMenus;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = TradingPostMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {

    private ClientSetup() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> MenuScreens.register(ModMenus.TRADING_POST_MENU.get(), TradingPostScreen::new));
    }

    @SubscribeEvent
    public static void onRegisterLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(ClientModelLayers.DELIVERY_DRONE, DeliveryDroneModel::createBodyLayer);
        event.registerLayerDefinition(ClientModelLayers.DELIVERY_PACKAGE, DeliveryPackageModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.DELIVERY_DRONE.get(), DeliveryDroneRenderer::new);
        event.registerEntityRenderer(ModEntities.DELIVERY_PACKAGE.get(), DeliveryPackageRenderer::new);
    }
}
