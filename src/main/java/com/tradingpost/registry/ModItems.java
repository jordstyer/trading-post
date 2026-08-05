package com.tradingpost.registry;

import com.tradingpost.TradingPostMod;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TradingPostMod.MODID);

    public static final RegistryObject<Item> TRADING_POST = ITEMS.register("trading_post",
            () -> new BlockItem(ModBlocks.TRADING_POST.get(), new Item.Properties()));

    public static final RegistryObject<Item> DELIVERY_CRATE = ITEMS.register("delivery_crate",
            () -> new BlockItem(ModBlocks.DELIVERY_CRATE.get(), new Item.Properties()));

    private ModItems() {
    }
}
