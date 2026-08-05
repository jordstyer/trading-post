package com.tradingpost.blockentity;

import com.tradingpost.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Holds exactly the items from one delivery. Filled once via {@link #setPayload} right after
 * placement, then behaves like a plain single chest. Once fully looted it removes itself
 * (see {@link #serverTick}) rather than sticking around as clutter.
 */
public class DeliveryCrateBlockEntity extends RandomizableContainerBlockEntity {

    /** Single-chest sized. Large payloads overflow into oversized stacks rather than more slots - see DeliveryService.buildPayload. */
    public static final int SLOTS = 27;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private boolean everFilled;

    public DeliveryCrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DELIVERY_CRATE.get(), pos, state);
    }

    /** Fills the crate with the purchased items, splitting oversized quantities across slots as needed. */
    public void setPayload(List<ItemStack> payload) {
        for (int i = 0; i < items.size() && i < payload.size(); i++) {
            items.set(i, payload.get(i));
        }
        everFilled = true;
        setChanged();
    }

    /** Removes the crate once it has been filled and fully emptied - never before it's ever held anything. */
    public static void serverTick(Level level, BlockPos pos, BlockState state, DeliveryCrateBlockEntity crate) {
        if (crate.everFilled && crate.isEmpty()) {
            level.removeBlock(pos, false);
        }
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.trading_post.delivery_crate");
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        everFilled = tag.getBoolean("EverFilled");
        if (!tryLoadLootTable(tag)) {
            ContainerHelper.loadAllItems(tag, items);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean("EverFilled", everFilled);
        if (!trySaveLootTable(tag)) {
            ContainerHelper.saveAllItems(tag, items);
        }
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return ChestMenu.threeRows(containerId, inventory, this);
    }
}
