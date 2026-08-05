package com.tradingpost.registry;

import com.tradingpost.TradingPostMod;
import com.tradingpost.menu.TradingPostMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {

    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, TradingPostMod.MODID);

    public static final RegistryObject<MenuType<TradingPostMenu>> TRADING_POST_MENU = MENUS.register("trading_post_menu",
            () -> IForgeMenuType.create((windowId, inv, data) -> new TradingPostMenu(windowId, inv, data)));

    private ModMenus() {
    }
}
