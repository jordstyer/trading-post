package com.tradingpost;

import com.tradingpost.config.TradingPostConfig;
import com.tradingpost.network.NetworkHandler;
import com.tradingpost.registry.ModBlockEntities;
import com.tradingpost.registry.ModBlocks;
import com.tradingpost.registry.ModCreativeTabs;
import com.tradingpost.registry.ModEntities;
import com.tradingpost.registry.ModItems;
import com.tradingpost.registry.ModMenus;
import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(TradingPostMod.MODID)
public class TradingPostMod {

    public static final String MODID = "trading_post";
    private static final Logger LOGGER = LogUtils.getLogger();

    public TradingPostMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModMenus.MENUS.register(modEventBus);
        ModCreativeTabs.CREATIVE_MODE_TABS.register(modEventBus);

        NetworkHandler.register();

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, TradingPostConfig.SPEC);

        LOGGER.info("Trading Post loaded");
    }
}
