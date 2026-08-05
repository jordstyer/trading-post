package com.tradingpost.client;

import com.tradingpost.TradingPostMod;
import com.tradingpost.entity.DeliveryPackageEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;

public class DeliveryPackageRenderer extends EntityRenderer<DeliveryPackageEntity> {

    private static final ResourceLocation TEXTURE = new ResourceLocation(TradingPostMod.MODID, "textures/entity/delivery_package.png");

    private final DeliveryPackageModel model;

    public DeliveryPackageRenderer(EntityRendererProvider.Context context) {
        super(context);
        ModelPart root = context.bakeLayer(ClientModelLayers.DELIVERY_PACKAGE);
        this.model = new DeliveryPackageModel(root);
    }

    @Override
    public void render(DeliveryPackageEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                        MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.scale(-1.0f, -1.0f, 1.0f);
        model.setupAnim(entity, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f);
        model.renderToBuffer(poseStack, buffer.getBuffer(model.renderType(TEXTURE)), packedLight,
                OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(DeliveryPackageEntity entity) {
        return TEXTURE;
    }
}
