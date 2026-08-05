package com.tradingpost.menu;

import com.tradingpost.market.Colony;
import com.tradingpost.market.MarketSavedData;
import com.tradingpost.network.MarketNetworking;
import com.tradingpost.registry.ModBlocks;
import com.tradingpost.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The trading menu. Holds no slots - items and emeralds move directly between the player's
 * inventory and the market via {@link com.tradingpost.network.C2SBuyPacket}/{@link com.tradingpost.network.C2SSellPacket},
 * never through container slots. What it holds is a client-displayable snapshot of every colony's
 * items, stock and price (refreshed after each trade) plus the two global price-band factors the
 * screen needs to compute live buy/sell quotes.
 */
public class TradingPostMenu extends AbstractContainerMenu {

    /** A single flattened list entry: which colony it belongs to, plus the item snapshot. */
    public record Row(String colonyId, String colonyDisplayName, MarketNetworking.EntrySnapshot entry) {
    }

    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final Map<String, MarketNetworking.ColonySnapshot> colonies = new LinkedHashMap<>();

    private double minPriceFactor = 0.5;
    private double maxPriceFactor = 2.0;

    /** Server-side: constructed by {@link com.tradingpost.blockentity.TradingPostBlockEntity#createMenu}. */
    public TradingPostMenu(int containerId, Inventory playerInventory, BlockPos pos) {
        super(ModMenus.TRADING_POST_MENU.get(), containerId);
        this.pos = pos;
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), pos);

        if (!playerInventory.player.level().isClientSide) {
            this.minPriceFactor = MarketNetworking.minPriceFactor();
            this.maxPriceFactor = MarketNetworking.maxPriceFactor();
            MarketSavedData market = MarketSavedData.get(playerInventory.player.getServer());
            for (Colony colony : market.getColonies()) {
                colonies.put(colony.getId(), MarketNetworking.snapshot(colony));
            }
        }
    }

    /** Client-side: constructed by the {@code IContainerFactory} registered in {@link ModMenus}. */
    public TradingPostMenu(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        super(ModMenus.TRADING_POST_MENU.get(), containerId);
        this.pos = buf.readBlockPos();
        this.access = ContainerLevelAccess.create(playerInventory.player.level(), pos);

        MarketNetworking.Market market = MarketNetworking.readMarket(buf);
        this.minPriceFactor = market.minPriceFactor();
        this.maxPriceFactor = market.maxPriceFactor();
        for (MarketNetworking.ColonySnapshot snapshot : market.colonies()) {
            colonies.put(snapshot.id(), snapshot);
        }
    }

    public BlockPos getPos() {
        return pos;
    }

    public double getMinPriceFactor() {
        return minPriceFactor;
    }

    public double getMaxPriceFactor() {
        return maxPriceFactor;
    }

    public java.util.Collection<MarketNetworking.ColonySnapshot> getColonies() {
        return colonies.values();
    }

    /** Every tradable line across all colonies, flattened for the unified scrolling list. */
    public List<Row> getAllRows() {
        List<Row> rows = new ArrayList<>();
        for (MarketNetworking.ColonySnapshot colony : colonies.values()) {
            for (MarketNetworking.EntrySnapshot entry : colony.entries()) {
                rows.add(new Row(colony.id(), colony.displayName(), entry));
            }
        }
        return rows;
    }

    /** Called client-side when a {@link com.tradingpost.network.S2CSyncMarketPacket} arrives. */
    public void updateColony(MarketNetworking.ColonySnapshot snapshot) {
        colonies.put(snapshot.id(), snapshot);
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.TRADING_POST.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        // No slots exist on this menu, so there is never anything to shift-click.
        return ItemStack.EMPTY;
    }
}
