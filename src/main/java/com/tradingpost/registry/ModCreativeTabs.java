package com.tradingpost.registry;

import com.tradingpost.TradingPostMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TradingPostMod.MODID);

    public static final RegistryObject<CreativeModeTab> TRADING_POST_TAB = CREATIVE_MODE_TABS.register("trading_post_tab",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.trading_post"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> ModItems.TRADING_POST.get().getDefaultInstance())
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.TRADING_POST.get());
                    }).build());

    private ModCreativeTabs() {
    }
}
