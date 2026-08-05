package com.tradingpost.registry;

import com.tradingpost.TradingPostMod;
import com.tradingpost.blockentity.DeliveryCrateBlockEntity;
import com.tradingpost.blockentity.TradingPostBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, TradingPostMod.MODID);

    public static final RegistryObject<BlockEntityType<TradingPostBlockEntity>> TRADING_POST =
            BLOCK_ENTITIES.register("trading_post", () -> BlockEntityType.Builder.of(
                    TradingPostBlockEntity::new, ModBlocks.TRADING_POST.get()).build(null));

    public static final RegistryObject<BlockEntityType<DeliveryCrateBlockEntity>> DELIVERY_CRATE =
            BLOCK_ENTITIES.register("delivery_crate", () -> BlockEntityType.Builder.of(
                    DeliveryCrateBlockEntity::new, ModBlocks.DELIVERY_CRATE.get()).build(null));

    private ModBlockEntities() {
    }
}
