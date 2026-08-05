package com.tradingpost.client;

import com.tradingpost.TradingPostMod;
import com.tradingpost.entity.DeliveryDroneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class DeliveryDroneRenderer extends EntityRenderer<DeliveryDroneEntity> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(TradingPostMod.MODID, "textures/entity/delivery_drone.png");

    private final DeliveryDroneModel model;

    public DeliveryDroneRenderer(EntityRendererProvider.Context context) {
        super(context);
        ModelPart root = context.bakeLayer(ClientModelLayers.DELIVERY_DRONE);
        this.model = new DeliveryDroneModel(root);
    }

    @Override
    public void render(DeliveryDroneEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                        MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        // Order matters and must match vanilla (LivingEntityRenderer rotates, then scales).
        // scale(-1,-1,1) is a 180-degree Z rotation, so scaling first mirrors the heading in X:
        // the model then reads correct facing north/south, backwards east/west, and sideways on
        // diagonals. Rotating first yields the true forward vector (-sin yaw, 0, cos yaw).
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0f - entityYaw));
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        // Pass real (interpolated) age so the propellers spin smoothly rather than stepping once per tick.
        model.setupAnim(entity, 0.0f, 0.0f, entity.tickCount + partialTick, 0.0f, 0.0f);
        model.renderToBuffer(poseStack, buffer.getBuffer(model.renderType(TEXTURE)), packedLight,
                OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(DeliveryDroneEntity entity) {
        return TEXTURE;
    }
}
