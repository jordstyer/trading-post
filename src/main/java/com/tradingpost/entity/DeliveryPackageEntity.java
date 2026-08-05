package com.tradingpost.entity;

import com.tradingpost.advancement.ModAdvancements;
import com.tradingpost.blockentity.DeliveryCrateBlockEntity;
import com.tradingpost.delivery.DeliveryService;
import com.tradingpost.registry.ModBlocks;
import com.tradingpost.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The parachute package a {@link DeliveryDroneEntity} drops overhead. Falls to the (already
 * validated) landing spot in two phases - a brief accelerating freefall, then a slow settle once
 * the canopy catches - with a lateral sway for wind drift, then places the
 * {@link com.tradingpost.block.DeliveryCrateBlock} and removes itself.
 *
 * <p>Like the plane, its motion is a pure function of its spawn parameters plus elapsed ticks,
 * synced once via {@link IEntityAdditionalSpawnData} so the client can simulate the identical
 * path locally instead of interpolating position packets. See {@link DeliveryDroneEntity} for why.
 */
public class DeliveryPackageEntity extends Entity implements IEntityAdditionalSpawnData {

    private static final String TAG_LANDING_POS = "LandingPos";
    private static final String TAG_RELEASE_POS = "ReleasePos";
    private static final String TAG_FALL_TICKS = "FallTicks";
    private static final String TAG_ELAPSED_TICKS = "ElapsedTicks";
    private static final String TAG_PAYLOAD = "Payload";
    private static final String TAG_SWAY_PHASE = "SwayPhase";
    private static final String TAG_BUYER = "Buyer";

    /** Fraction of the fall spent in freefall before the canopy takes hold. */
    private static final double FREEFALL_TIME = 0.18;
    /** Fraction of the total drop height covered during that freefall. */
    private static final double FREEFALL_DISTANCE = 0.22;

    private List<ItemStack> payload = new ArrayList<>();
    private BlockPos landingPos = BlockPos.ZERO;
    private Vec3 releasePos = Vec3.ZERO;
    private int fallTicks = 80;
    private int elapsedTicks;
    private double swayPhase;
    private boolean delivered;
    /** Who bought this, so the airdrop advancement lands on the right player. Null-safe. */
    private UUID buyerId;

    public DeliveryPackageEntity(EntityType<? extends DeliveryPackageEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    public static DeliveryPackageEntity spawn(ServerLevel level, Vec3 releasePos, BlockPos landingPos,
                                               List<ItemStack> payload, int fallTicks, UUID buyerId) {
        DeliveryPackageEntity pkg = new DeliveryPackageEntity(ModEntities.DELIVERY_PACKAGE.get(), level);
        pkg.releasePos = releasePos;
        pkg.landingPos = landingPos.immutable();
        pkg.payload = payload;
        pkg.fallTicks = Math.max(1, fallTicks);
        pkg.buyerId = buyerId;
        pkg.swayPhase = level.getRandom().nextDouble() * Math.PI * 2.0;
        pkg.setPos(releasePos.x, releasePos.y, releasePos.z);

        level.addFreshEntity(pkg);
        return pkg;
    }

    /**
     * Fraction of the drop completed at normalised time {@code t}. Deliberately not a single
     * smooth ease: the velocity break at {@link #FREEFALL_TIME} is what reads as the canopy
     * snapping open.
     */
    private static double fallCurve(double t) {
        if (t <= FREEFALL_TIME) {
            double k = t / FREEFALL_TIME;
            return FREEFALL_DISTANCE * k * k;
        }
        double k = (t - FREEFALL_TIME) / (1.0 - FREEFALL_TIME);
        double settle = 1.0 - Math.pow(1.0 - k, 1.6);
        return FREEFALL_DISTANCE + (1.0 - FREEFALL_DISTANCE) * settle;
    }

    /**
     * Position at a given (possibly fractional) tick count into the fall.
     *
     * <p>The Y target is {@code landingPos.getY()} exactly, not one above it: the crate model
     * extends a full block <em>upward</em> from the entity origin, so ending here makes the
     * falling crate occupy precisely the volume the {@link com.tradingpost.block.DeliveryCrateBlock}
     * will fill. Ending a block higher would make the crate visibly drop as it turned into the block.
     */
    private Vec3 pathPosition(double ticks) {
        double t = Mth.clamp(ticks / fallTicks, 0.0, 1.0);
        double progress = fallCurve(t);
        double sway = Math.sin(ticks * 0.12 + swayPhase) * (1.0 - progress) * 0.4;
        return new Vec3(
                Mth.lerp(progress, releasePos.x, landingPos.getX() + 0.5) + sway,
                Mth.lerp(progress, releasePos.y, landingPos.getY()),
                Mth.lerp(progress, releasePos.z, landingPos.getZ() + 0.5) + sway);
    }

    @Override
    public void tick() {
        super.tick();
        if (delivered) {
            return;
        }

        elapsedTicks++;
        Vec3 pos = pathPosition(elapsedTicks);
        setPos(pos.x, pos.y, pos.z);

        if (!level().isClientSide && elapsedTicks >= fallTicks) {
            deliver();
        }
    }

    private void deliver() {
        delivered = true;
        ServerLevel serverLevel = (ServerLevel) level();
        DeliveryService.releaseLandingSpot(landingPos);

        // Never overwrite an existing crate: replacing a block with the same block skips
        // DeliveryCrateBlock.onRemove's drop path, so the old contents would vanish silently.
        // claimLandingSpot hands back a free neighbour instead, or null if there's genuinely
        // nowhere - in which case the goods spill as items rather than being destroyed.
        BlockPos target = DeliveryService.claimLandingSpot(serverLevel, landingPos);
        if (target == null) {
            for (ItemStack stack : payload) {
                Containers.dropItemStack(serverLevel, landingPos.getX() + 0.5,
                        landingPos.getY() + 0.5, landingPos.getZ() + 0.5, stack);
            }
            discard();
            return;
        }

        serverLevel.setBlockAndUpdate(target, ModBlocks.DELIVERY_CRATE.get().defaultBlockState());
        if (serverLevel.getBlockEntity(target) instanceof DeliveryCrateBlockEntity crate) {
            crate.setPayload(payload);
        }
        // No landing sound on purpose: the touchdown thud read as too loud/intrusive in play.
        // The crate's own chest open/close sounds (DeliveryCrateBlockEntity) carry the moment.
        awardDeliveryAdvancement(serverLevel);
        discard();
    }

    /** Credits the buyer once their airdrop actually lands - not merely when they ordered it. */
    private void awardDeliveryAdvancement(ServerLevel serverLevel) {
        if (buyerId == null) {
            return;
        }
        // Null if they logged off mid-flight; the advancement is simply skipped in that case.
        ServerPlayer buyer = serverLevel.getServer().getPlayerList().getPlayer(buyerId);
        if (buyer != null) {
            ModAdvancements.award(buyer, ModAdvancements.FIRST_DELIVERY);
        }
    }

    /** No-op for the same reason as {@link DeliveryDroneEntity#lerpTo}. */
    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buffer) {
        buffer.writeDouble(releasePos.x);
        buffer.writeDouble(releasePos.y);
        buffer.writeDouble(releasePos.z);
        buffer.writeBlockPos(landingPos);
        buffer.writeVarInt(fallTicks);
        buffer.writeVarInt(elapsedTicks);
        buffer.writeDouble(swayPhase);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buffer) {
        releasePos = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        landingPos = buffer.readBlockPos();
        fallTicks = Math.max(1, buffer.readVarInt());
        elapsedTicks = buffer.readVarInt();
        swayPhase = buffer.readDouble();

        Vec3 pos = pathPosition(elapsedTicks);
        setPos(pos.x, pos.y, pos.z);
        xOld = pos.x;
        yOld = pos.y;
        zOld = pos.z;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canCollideWith(Entity other) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void defineSynchedData() {
        // See DeliveryDroneEntity: path parameters are spawn-time immutable.
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        landingPos = BlockPos.of(tag.getLong(TAG_LANDING_POS));
        releasePos = new Vec3(tag.getDouble(TAG_RELEASE_POS + "X"), tag.getDouble(TAG_RELEASE_POS + "Y"),
                tag.getDouble(TAG_RELEASE_POS + "Z"));
        fallTicks = Math.max(1, tag.getInt(TAG_FALL_TICKS));
        elapsedTicks = tag.getInt(TAG_ELAPSED_TICKS);
        swayPhase = tag.getDouble(TAG_SWAY_PHASE);
        buyerId = tag.hasUUID(TAG_BUYER) ? tag.getUUID(TAG_BUYER) : null;

        payload = new ArrayList<>();
        ListTag list = tag.getList(TAG_PAYLOAD, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            payload.add(ItemStack.of(list.getCompound(i)));
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putLong(TAG_LANDING_POS, landingPos.asLong());
        tag.putDouble(TAG_RELEASE_POS + "X", releasePos.x);
        tag.putDouble(TAG_RELEASE_POS + "Y", releasePos.y);
        tag.putDouble(TAG_RELEASE_POS + "Z", releasePos.z);
        tag.putInt(TAG_FALL_TICKS, fallTicks);
        tag.putInt(TAG_ELAPSED_TICKS, elapsedTicks);
        tag.putDouble(TAG_SWAY_PHASE, swayPhase);
        if (buyerId != null) {
            tag.putUUID(TAG_BUYER, buyerId);
        }

        ListTag list = new ListTag();
        for (ItemStack stack : payload) {
            list.add(stack.save(new CompoundTag()));
        }
        tag.put(TAG_PAYLOAD, list);
    }
}
