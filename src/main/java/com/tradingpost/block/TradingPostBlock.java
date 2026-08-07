package com.tradingpost.block;

import com.tradingpost.blockentity.TradingPostBlockEntity;
import com.tradingpost.market.MarketSavedData;
import com.tradingpost.network.MarketNetworking;
import com.tradingpost.network.NetworkHandler;
import com.tradingpost.network.S2COpenMarketPacket;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * The Trading Post: right-clicking opens the market GUI. All menu logic runs server-side; the
 * client only ever receives the data it's explicitly sent. The block faces the player when placed
 * (like a furnace) so its lettered front is visible.
 */
public class TradingPostBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public static BlockBehaviour.Properties defaultProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(2.5f)
                // The model is a desk, not a full cube - without this the game would treat it as
                // solid for lighting/face-culling and neighbouring blocks would render wrong.
                .noOcclusion();
        // Deliberately NOT requiresCorrectToolForDrops(): that flag only works alongside a
        // mineable/* tag, because isCorrectToolForDrops checks `state.is(tool.blocks)`. With the
        // flag set and no tag - which is how this shipped at first - no tool ever counts as
        // correct, so the block mines at the penalty speed and drops nothing at all. It's a
        // wooden workstation, so it now behaves like a crafting table: hand-harvestable, and
        // faster with an axe via the minecraft:mineable/axe tag in data/minecraft/tags.
    }

    public TradingPostBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TradingPostBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity instanceof MenuProvider menuProvider) {
                // Open with ONLY the position - NetworkHooks.openScreen's extra-data buffer is
                // hard-capped at 32,600 bytes, and the full catalog (6 colonies, 200+ items, more
                // with datapack overrides) serializes to roughly 90-100KB. Sending it through here
                // threw IllegalArgumentException server-side on every single click, which the
                // player experienced as the block silently doing nothing - the only trace was a
                // stack trace in the server log, since the client never even got a response.
                ServerPlayer serverPlayer = (ServerPlayer) player;
                NetworkHooks.openScreen(serverPlayer, menuProvider, pos);

                MarketSavedData market = MarketSavedData.get(level.getServer());
                NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> serverPlayer),
                        new S2COpenMarketPacket(MarketNetworking.currentMarket(market.getColonies())));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
