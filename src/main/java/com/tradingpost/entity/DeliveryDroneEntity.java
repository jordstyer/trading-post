package com.tradingpost.entity;

import com.tradingpost.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
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
 * A short-lived, non-solid, non-AI cargo plane: flies a straight line at a fixed altitude high
 * above the landing spot, entering from one side and exiting the other. When it's directly
 * overhead the landing spot it releases a {@link DeliveryPackageEntity} carrying the purchase,
 * then keeps flying its full departure run and despawns.
 *
 * <p>Motion is a deterministic function of (entry, exit, total ticks, elapsed ticks), and those
 * parameters are sent to the client on spawn via {@link IEntityAdditionalSpawnData}. That lets
 * the client run the exact same path locally instead of chasing interpolated position packets -
 * which is what makes the flight look smooth rather than stuttering between network updates.
 * {@link #lerpTo} is deliberately a no-op for the same reason.
 */
public class DeliveryDroneEntity extends Entity implements IEntityAdditionalSpawnData {

    /** Ticks between engine-drone plays; roughly the length of the sound itself. */
    private static final int ENGINE_SOUND_INTERVAL = 40;

    private static final String TAG_LANDING_POS = "LandingPos";
    private static final String TAG_ENTRY_POS = "EntryPos";
    private static final String TAG_EXIT_POS = "ExitPos";
    private static final String TAG_FLIGHT_TICKS = "FlightTicksTotal";
    private static final String TAG_ELAPSED_TICKS = "ElapsedTicks";
    private static final String TAG_FALL_TICKS = "FallTicks";
    private static final String TAG_PAYLOAD = "Payload";
    private static final String TAG_RELEASED = "Released";
    private static final String TAG_BUYER = "Buyer";

    private List<ItemStack> payload = new ArrayList<>();
    private BlockPos landingPos = BlockPos.ZERO;
    private Vec3 entryPos = Vec3.ZERO;
    private Vec3 exitPos = Vec3.ZERO;
    private int flightTicksTotal = 200;
    private int fallTicks = 80;
    private int elapsedTicks;
    private boolean released;
    /** Who bought this, so the airdrop advancement lands on the right player. Null-safe. */
    private UUID buyerId;

    public DeliveryDroneEntity(EntityType<? extends DeliveryDroneEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
    }

    /**
     * Spawns a plane on a straight-line pass over {@code landingPos}, carrying {@code payload} to
     * release at the midpoint - so the approach and departure runs are the same length. Heading,
     * altitude and duration are all pre-computed by {@link com.tradingpost.delivery.DeliveryService}.
     */
    public static DeliveryDroneEntity spawn(ServerLevel level, BlockPos landingPos, List<ItemStack> payload,
                                             double headingRadians, int altitude, int halfLength, int flightTicks,
                                             int fallTicks, UUID buyerId) {
        DeliveryDroneEntity plane = new DeliveryDroneEntity(ModEntities.DELIVERY_DRONE.get(), level);
        plane.landingPos = landingPos.immutable();
        plane.payload = payload;
        plane.flightTicksTotal = Math.max(2, flightTicks);
        plane.fallTicks = Math.max(1, fallTicks);
        plane.buyerId = buyerId;

        double dirX = -Math.sin(headingRadians);
        double dirZ = Math.cos(headingRadians);
        Vec3 overhead = new Vec3(landingPos.getX() + 0.5, landingPos.getY() + altitude, landingPos.getZ() + 0.5);
        plane.entryPos = overhead.subtract(dirX * halfLength, 0, dirZ * halfLength);
        plane.exitPos = overhead.add(dirX * halfLength, 0, dirZ * halfLength);
        plane.setPos(plane.entryPos.x, plane.entryPos.y, plane.entryPos.z);
        plane.setYRot((float) Math.toDegrees(Math.atan2(-dirX, dirZ)));
        plane.yRotO = plane.getYRot();

        level.addFreshEntity(plane);
        return plane;
    }

    /** Position along the flight path at a given (possibly fractional) tick count. */
    private Vec3 pathPosition(double ticks) {
        double t = Mth.clamp(ticks / flightTicksTotal, 0.0, 1.0);
        return new Vec3(
                Mth.lerp(t, entryPos.x, exitPos.x),
                Mth.lerp(t, entryPos.y, exitPos.y),
                Mth.lerp(t, entryPos.z, exitPos.z));
    }

    @Override
    public void tick() {
        super.tick();

        elapsedTicks++;
        Vec3 pos = pathPosition(elapsedTicks);
        setPos(pos.x, pos.y, pos.z);

        // Only the server owns delivery, sound and lifetime; the client just flies the same path.
        if (level().isClientSide) {
            return;
        }

        // Engine drone. Volume is deliberately far above 1: Minecraft attenuates sound over
        // roughly 16 * volume blocks, and the plane cruises 40+ blocks up, so a normal volume
        // would simply never reach the ground.
        if (elapsedTicks % ENGINE_SOUND_INTERVAL == 0) {
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.MINECART_INSIDE,
                    SoundSource.NEUTRAL, 3.0f, 0.55f);
        }

        double t = (double) elapsedTicks / flightTicksTotal;
        if (!released && t >= 0.5) {
            release();
        }
        if (t >= 1.0) {
            discard();
        }
    }

    private void release() {
        released = true;
        DeliveryPackageEntity.spawn((ServerLevel) level(), position(), landingPos, payload, fallTicks, buyerId);
        // Canopy snapping open, again boosted so it carries down from cruise altitude.
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ELYTRA_FLYING,
                SoundSource.NEUTRAL, 2.5f, 0.8f);
    }

    /**
     * No-op: the client reproduces the flight path exactly, so applying interpolation from
     * position packets on top of it would only fight the local simulation and cause stutter.
     */
    @Override
    public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps, boolean teleport) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buffer) {
        buffer.writeDouble(entryPos.x);
        buffer.writeDouble(entryPos.y);
        buffer.writeDouble(entryPos.z);
        buffer.writeDouble(exitPos.x);
        buffer.writeDouble(exitPos.y);
        buffer.writeDouble(exitPos.z);
        buffer.writeVarInt(flightTicksTotal);
        buffer.writeVarInt(elapsedTicks);
        buffer.writeFloat(getYRot());
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buffer) {
        entryPos = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        exitPos = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        flightTicksTotal = Math.max(2, buffer.readVarInt());
        elapsedTicks = buffer.readVarInt();
        float yaw = buffer.readFloat();
        setYRot(yaw);
        yRotO = yaw;
        // Snap to the correct point on the path immediately, so a plane that comes into view
        // mid-flight appears where it should rather than at its spawn point.
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
        // Path parameters are immutable for the entity's lifetime, so they go through
        // IEntityAdditionalSpawnData once at spawn rather than the synched-data system.
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        landingPos = BlockPos.of(tag.getLong(TAG_LANDING_POS));
        entryPos = new Vec3(tag.getDouble(TAG_ENTRY_POS + "X"), tag.getDouble(TAG_ENTRY_POS + "Y"), tag.getDouble(TAG_ENTRY_POS + "Z"));
        exitPos = new Vec3(tag.getDouble(TAG_EXIT_POS + "X"), tag.getDouble(TAG_EXIT_POS + "Y"), tag.getDouble(TAG_EXIT_POS + "Z"));
        flightTicksTotal = Math.max(2, tag.getInt(TAG_FLIGHT_TICKS));
        fallTicks = tag.getInt(TAG_FALL_TICKS);
        elapsedTicks = tag.getInt(TAG_ELAPSED_TICKS);
        released = tag.getBoolean(TAG_RELEASED);
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
        tag.putDouble(TAG_ENTRY_POS + "X", entryPos.x);
        tag.putDouble(TAG_ENTRY_POS + "Y", entryPos.y);
        tag.putDouble(TAG_ENTRY_POS + "Z", entryPos.z);
        tag.putDouble(TAG_EXIT_POS + "X", exitPos.x);
        tag.putDouble(TAG_EXIT_POS + "Y", exitPos.y);
        tag.putDouble(TAG_EXIT_POS + "Z", exitPos.z);
        tag.putInt(TAG_FLIGHT_TICKS, flightTicksTotal);
        tag.putInt(TAG_FALL_TICKS, fallTicks);
        tag.putInt(TAG_ELAPSED_TICKS, elapsedTicks);
        tag.putBoolean(TAG_RELEASED, released);
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
