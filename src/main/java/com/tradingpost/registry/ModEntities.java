package com.tradingpost.registry;

import com.tradingpost.TradingPostMod;
import com.tradingpost.entity.DeliveryDroneEntity;
import com.tradingpost.entity.DeliveryPackageEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, TradingPostMod.MODID);

    // Tracking range is requested high and then clamped by the server's view distance, so the
    // plane is sent to clients as far out as they can actually render it. Update interval is
    // slow on purpose: both entities simulate their own deterministic path client-side (see
    // DeliveryDroneEntity), so frequent position packets would be wasted bandwidth.
    public static final RegistryObject<EntityType<DeliveryDroneEntity>> DELIVERY_DRONE = ENTITY_TYPES.register(
            "delivery_drone", () -> EntityType.Builder.<DeliveryDroneEntity>of(DeliveryDroneEntity::new, MobCategory.MISC)
                    .sized(3.0f, 1.0f)
                    .clientTrackingRange(32)
                    .updateInterval(20)
                    .build("delivery_drone"));

    // Sized for a full-block crate plus the canopy above it - the crate matches
    // DeliveryCrateBlock exactly so it doesn't change size on landing.
    public static final RegistryObject<EntityType<DeliveryPackageEntity>> DELIVERY_PACKAGE = ENTITY_TYPES.register(
            "delivery_package", () -> EntityType.Builder.<DeliveryPackageEntity>of(DeliveryPackageEntity::new, MobCategory.MISC)
                    .sized(1.75f, 2.75f)
                    .clientTrackingRange(32)
                    .updateInterval(20)
                    .build("delivery_package"));

    private ModEntities() {
    }
}
