package com.tradingpost.client;

import com.tradingpost.entity.DeliveryDroneEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;

/**
 * A fixed-wing cargo plane, hand-built here rather than in Blockbench. Roughly 2.5 blocks long
 * with a 3-block wingspan, which is what it takes to stay legible at the flight altitudes in
 * {@link com.tradingpost.config.TradingPostConfig}.
 *
 * <p>Orientation: the nose points along -Z. That matches the convention the renderer's
 * {@code scale(-1,-1,1)} + {@code rotationDegrees(180 - entityYaw)} transform expects, so the
 * model ends up flying nose-first along the entity's heading. Model -Y is world up.
 *
 * <p>texOffs values here must stay in sync with {@code scripts/gen_delivery_textures.py}, which
 * paints the atlas regions those offsets point at.
 */
public class DeliveryDroneModel extends EntityModel<DeliveryDroneEntity> {

    /** Lift strut rake, in radians - matches the wing/fuselage geometry below. */
    private static final float STRUT_ANGLE = 0.38f;

    private final ModelPart body;
    private final ModelPart wing;
    private final ModelPart tail;
    private final ModelPart nacelles;
    private final ModelPart strutLeft;
    private final ModelPart strutRight;
    private final ModelPart propLeft;
    private final ModelPart propRight;

    public DeliveryDroneModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.body = root.getChild("body");
        this.wing = root.getChild("wing");
        this.tail = root.getChild("tail");
        this.nacelles = root.getChild("nacelles");
        this.strutLeft = root.getChild("strut_left");
        this.strutRight = root.getChild("strut_right");
        this.propLeft = root.getChild("prop_left");
        this.propRight = root.getChild("prop_right");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Fuselage plus the tapered nose cap ahead of it.
        root.addOrReplaceChild("body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-3.0f, -3.0f, -20.0f, 6, 6, 40)
                        .texOffs(92, 0).addBox(-2.0f, -2.0f, -24.0f, 4, 4, 4),
                PartPose.ZERO);

        // High-mounted wing, sitting on top of the fuselage.
        root.addOrReplaceChild("wing", CubeListBuilder.create()
                        .texOffs(0, 48).addBox(-24.0f, -5.0f, -6.0f, 48, 2, 12),
                PartPose.ZERO);

        // Horizontal stabiliser + vertical fin at the tail.
        root.addOrReplaceChild("tail", CubeListBuilder.create()
                        .texOffs(0, 64).addBox(-11.0f, -1.0f, 14.0f, 22, 2, 8)
                        .texOffs(62, 64).addBox(-1.0f, -11.0f, 14.0f, 2, 10, 10),
                PartPose.ZERO);

        // Underslung engine nacelles, one per wing.
        root.addOrReplaceChild("nacelles", CubeListBuilder.create()
                        .texOffs(0, 76).addBox(-16.5f, -6.0f, -14.0f, 5, 5, 14)
                        .texOffs(0, 76).addBox(11.5f, -6.0f, -14.0f, 5, 5, 14),
                PartPose.ZERO);

        // Lift struts bracing the high wing back to the lower fuselage - the detail that most
        // reads as "cargo plane" rather than "flying box" from below, which is the angle the
        // player almost always sees this from.
        root.addOrReplaceChild("strut_left", strut(),
                PartPose.offsetAndRotation(-8.0f, -1.0f, -2.0f, 0.0f, 0.0f, STRUT_ANGLE));
        root.addOrReplaceChild("strut_right", strut(),
                PartPose.offsetAndRotation(8.0f, -1.0f, -2.0f, 0.0f, 0.0f, -STRUT_ANGLE));

        root.addOrReplaceChild("prop_left", propeller(), PartPose.offset(-14.0f, -3.5f, -15.0f));
        root.addOrReplaceChild("prop_right", propeller(), PartPose.offset(14.0f, -3.5f, -15.0f));

        return LayerDefinition.create(mesh, 128, 128);
    }

    /** A single lift strut, lying along X about its midpoint so the pose rotation rakes it. */
    private static CubeListBuilder strut() {
        return CubeListBuilder.create()
                .texOffs(84, 96).addBox(-5.5f, -0.5f, -1.0f, 11, 1, 2);
    }

    /** Two crossed blades around the hub; spun about Z in {@link #setupAnim}. */
    private static CubeListBuilder propeller() {
        return CubeListBuilder.create()
                .texOffs(40, 96).addBox(-7.0f, -0.5f, -1.0f, 14, 1, 2)
                .texOffs(74, 96).addBox(-0.5f, -7.0f, -1.0f, 1, 14, 2);
    }

    @Override
    public void setupAnim(DeliveryDroneEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                           float netHeadYaw, float headPitch) {
        // Only the props move; the airframe itself is rigid and the flight path supplies the rest.
        float spin = ageInTicks * 1.4f;
        propLeft.zRot = spin;
        propRight.zRot = -spin;
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                                float red, float green, float blue, float alpha) {
        body.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        wing.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        tail.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        nacelles.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        strutLeft.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        strutRight.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        propLeft.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        propRight.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
