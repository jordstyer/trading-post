package com.tradingpost.client;

import com.tradingpost.entity.DeliveryPackageEntity;
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

import java.util.ArrayList;
import java.util.List;

/**
 * The airdropped crate under a parachute. The canopy is a top cap with eight panels tilted down
 * and out from it in two interleaved tiers, which gives a domed silhouette from any angle without
 * needing real non-box geometry; four cords run from the skirt down to the crate corners.
 *
 * <p><b>The crate deliberately mirrors {@link com.tradingpost.block.DeliveryCrateBlock} exactly</b>
 * - full block size (16 units, not a half-size box), same inset body + overhanging lid + corner
 * post construction, and faces cut from the very same block textures (see
 * {@code scripts/gen_textures.py}, which copies those pixels into this atlas rather than
 * re-painting a lookalike). Anything else makes the package visibly pop/change size at the moment
 * it lands and becomes the block.
 *
 * <p>Model -Y is world up (the renderer applies {@code scale(-1,-1,1)}), so the crate occupies
 * y -16..0 - one block tall, sitting on the landing position - and the canopy is above it at more
 * negative y. texOffs values must stay in sync with {@code scripts/gen_textures.py}.
 */
public class DeliveryPackageModel extends EntityModel<DeliveryPackageEntity> {

    /** Upper tier: shallow rake, straight out from the cap. */
    private static final float UPPER_TILT = 0.42f;
    /** Lower tier: steeper rake, forming the skirt. Offset 45 degrees from the upper tier. */
    private static final float LOWER_TILT = 0.85f;
    /** Outward lean of the shroud cords, in radians. */
    private static final float CORD_LEAN = 0.12f;
    private static final float HALF_PI = (float) (Math.PI / 2.0);

    private final ModelPart crateBody;
    private final ModelPart crateLid;
    private final ModelPart canopy;
    private final List<ModelPart> posts = new ArrayList<>();
    private final List<ModelPart> panels = new ArrayList<>();
    private final List<ModelPart> cords = new ArrayList<>();

    public DeliveryPackageModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.crateBody = root.getChild("crate_body");
        this.crateLid = root.getChild("crate_lid");
        this.canopy = root.getChild("canopy");
        for (int i = 0; i < 8; i++) {
            panels.add(root.getChild("panel" + i));
        }
        for (int i = 0; i < 4; i++) {
            posts.add(root.getChild("post" + i));
            cords.add(root.getChild("cord" + i));
        }
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Crate, matching DeliveryCrateBlock's model one-for-one: body inset 1 unit, a full-width
        // lid overhanging it, and four corner posts standing proud of the body. Occupies y -16..0,
        // i.e. exactly the one-block volume the landed block will fill.
        root.addOrReplaceChild("crate_body", CubeListBuilder.create()
                        .texOffs(0, 0).addBox(-7.0f, -13.0f, -7.0f, 14, 13, 14),
                PartPose.ZERO);
        root.addOrReplaceChild("crate_lid", CubeListBuilder.create()
                        .texOffs(0, 28).addBox(-8.0f, -16.0f, -8.0f, 16, 3, 16),
                PartPose.ZERO);
        for (int i = 0; i < 4; i++) {
            float signX = (i == 0 || i == 1) ? 1.0f : -1.0f;
            float signZ = (i == 0 || i == 3) ? 1.0f : -1.0f;
            root.addOrReplaceChild("post" + i, CubeListBuilder.create()
                            .texOffs(64, 0).addBox(-1.0f, -13.0f, -1.0f, 2, 13, 2),
                    PartPose.offset(signX * 7.0f, 0.0f, signZ * 7.0f));
        }

        // Cap crowning the dome, sized to stay in proportion with the full-block crate.
        root.addOrReplaceChild("canopy", CubeListBuilder.create()
                        .texOffs(0, 48).addBox(-8.0f, -40.0f, -8.0f, 16, 2, 16),
                PartPose.ZERO);

        // Eight panels in two tiers: four shallow ones on the cardinal axes, four steeper ones
        // rotated 45 degrees between them. That interleaving is what turns a 4-sided pyramid into
        // something that reads as a round canopy from any angle. Each panel is tilted about X
        // first and then spun by its yaw - PartPose applies Z, then Y, then X, so the tilt happens
        // in local space and the yaw carries it around, letting one definition serve every panel.
        for (int i = 0; i < 4; i++) {
            root.addOrReplaceChild("panel" + i, CubeListBuilder.create()
                            .texOffs(0, 68).addBox(-9.0f, -0.5f, -13.0f, 18, 1, 13),
                    PartPose.offsetAndRotation(0.0f, -37.0f, 0.0f, UPPER_TILT, i * HALF_PI, 0.0f));
        }
        for (int i = 0; i < 4; i++) {
            root.addOrReplaceChild("panel" + (i + 4), CubeListBuilder.create()
                            .texOffs(0, 84).addBox(-9.0f, -0.5f, -16.0f, 18, 1, 16),
                    PartPose.offsetAndRotation(0.0f, -36.0f, 0.0f, LOWER_TILT,
                            i * HALF_PI + HALF_PI / 2.0f, 0.0f));
        }

        // Shroud cords at the crate's four top corners, leaning outward toward the skirt.
        for (int i = 0; i < 4; i++) {
            float signX = (i == 0 || i == 1) ? 1.0f : -1.0f;
            float signZ = (i == 0 || i == 3) ? 1.0f : -1.0f;
            root.addOrReplaceChild("cord" + i, CubeListBuilder.create()
                            .texOffs(70, 68).addBox(-0.5f, -8.0f, -0.5f, 1, 16, 1),
                    PartPose.offsetAndRotation(signX * 7.0f, -24.0f, signZ * 7.0f,
                            -signZ * CORD_LEAN, 0.0f, signX * CORD_LEAN));
        }

        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override
    public void setupAnim(DeliveryPackageEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks,
                           float netHeadYaw, float headPitch) {
        // Static model - the entity's fall path already carries the drift and sway.
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay,
                                float red, float green, float blue, float alpha) {
        crateBody.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        crateLid.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        for (ModelPart post : posts) {
            post.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        }
        canopy.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        for (ModelPart panel : panels) {
            panel.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        }
        for (ModelPart cord : cords) {
            cord.render(poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        }
    }
}
