package com.tradingpost.registry;

import com.tradingpost.TradingPostMod;
import com.tradingpost.block.DeliveryCrateBlock;
import com.tradingpost.block.TradingPostBlock;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, TradingPostMod.MODID);

    public static final RegistryObject<Block> TRADING_POST = BLOCKS.register("trading_post",
            () -> new TradingPostBlock(TradingPostBlock.defaultProperties()));

    public static final RegistryObject<Block> DELIVERY_CRATE = BLOCKS.register("delivery_crate",
            () -> new DeliveryCrateBlock(DeliveryCrateBlock.defaultProperties()));

    private ModBlocks() {
    }
}
