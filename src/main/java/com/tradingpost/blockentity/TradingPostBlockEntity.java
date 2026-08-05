package com.tradingpost.blockentity;

import com.tradingpost.menu.TradingPostMenu;
import com.tradingpost.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The table itself holds no market state - it is only a menu provider and a physical access
 * point. All prices/supply live in {@link com.tradingpost.market.MarketSavedData} on the overworld,
 * shared by every table and every player.
 */
public class TradingPostBlockEntity extends BlockEntity implements MenuProvider {

    public TradingPostBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TRADING_POST.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("menu.trading_post.trading_post");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new TradingPostMenu(containerId, playerInventory, this.getBlockPos());
    }
}
