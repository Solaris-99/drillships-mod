package com.solaris99.drillship.client;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.solaris99.drillship.Drillships;
import com.solaris99.drillship.client.model.MeshBuilder;
import com.solaris99.drillship.client.model.SectionModel;
import com.solaris99.drillship.entity.DrillshipEntity;
import com.solaris99.drillship.entity.ShipSectionEntity;
import com.solaris99.drillship.entity.WagonEntity;
import com.solaris99.drillship.ship.ShipShape;
import com.solaris99.drillship.ship.ShipTier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;

/**
 * A section's exterior (see {@link SectionModel}); the drillhead adds its spinning drill (see {@link DrillMesh}) and
 * each wagon the bellows to the section in front of it. One instance per section type.
 */
public class ShipSectionRenderer<T extends ShipSectionEntity> extends EntityRenderer<T> {
    // A camera this close to a section, or inside it (the driver's periscope, a third-person camera pulled in by a
    // tunnel), would only see the inside of its walls, so the section isn't drawn.
    private static final double CAMERA_CLEARANCE = 0.2;
    private static final int BEACON_BLINK_TICKS = 8;

    private final Map<ShipTier, SectionModel.Spec> specs = new EnumMap<>(ShipTier.class);
    private final Map<ShipTier, MeshBuilder> statics = new EnumMap<>(ShipTier.class);
    private final Map<ShipTier, DrillMesh> drills = new EnumMap<>(ShipTier.class);

    public ShipSectionRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(T ship, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        ShipShape shape = ship.shape();
        float yaw = Mth.rotLerp(partialTick, ship.yRotO, ship.getYRot());
        float pitch = Mth.lerp(partialTick, ship.xRotO, ship.getXRot());
        Vec3 camera = ShipShape.Frame.of(ship.getPosition(partialTick), yaw, pitch).toLocal(entityRenderDispatcher.camera.getPosition());
        if (shape.whole().inflate(CAMERA_CLEARANCE).contains(camera)) {
            return;
        }
        SectionModel.Spec spec = specs.computeIfAbsent(ship.tier(), tier -> spec(ship, shape));
        Sprites sprites = new Sprites();
        VertexConsumer consumer = buffers.getBuffer(RenderType.entitySolid(InventoryMenu.BLOCK_ATLAS));
        poseStack.pushPose();
        poseStack.mulPose(rotation(yaw, pitch));
        PoseStack.Pose pose = poseStack.last();

        emit(consumer, pose, statics.computeIfAbsent(ship.tier(), tier -> SectionModel.buildStatic(spec)), sprites, packedLight);
        boolean lights = ship instanceof DrillshipEntity drillhead && drillhead.isEngineOn();
        boolean beacon = ship instanceof DrillshipEntity drillhead && drillhead.isWarning() && (ship.tickCount / BEACON_BLINK_TICKS) % 2 == 0;
        int modules = ship instanceof DrillshipEntity drillhead ? drillhead.modules() : 0;
        emit(consumer, pose, SectionModel.buildMoving(spec, ship.getTravel(partialTick), ship.getDoorOpen(partialTick), lights, beacon, modules), sprites, packedLight);

        if (ship instanceof WagonEntity wagon) {
            renderBellows(wagon, spec, yaw, pitch, partialTick, consumer, pose, sprites, packedLight);
        }
        if (ship instanceof DrillshipEntity drillhead) {
            renderDrill(drillhead, shape, partialTick, poseStack, consumer, sprites, packedLight);
        }
        poseStack.popPose();
        super.render(ship, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    private static SectionModel.Spec spec(ShipSectionEntity ship, ShipShape shape) {
        ShipTier.Coupling coupling = ship.tier().coupling();
        int width = Mth.ceil(shape.halfWidth * 2);
        return new SectionModel.Spec(ship instanceof DrillshipEntity, width, Mth.ceil(shape.height), Mth.ceil(shape.hullLength()),
                (float) shape.back, coupling.taper(), Math.max(1.25F, 0.22F * width), coupling.passageWidth(), coupling.passageHeight());
    }

    // The same rotation as the pose: section-local coordinates to world directions.
    private static Quaternionf rotation(float yaw, float pitch) {
        return Axis.YP.rotationDegrees(-yaw).mul(Axis.XP.rotationDegrees(pitch));
    }

    // The bellows run from this wagon's front mouth to the leader's back mouth, taken into this wagon's coordinates.
    private void renderBellows(WagonEntity wagon, SectionModel.Spec spec, float yaw, float pitch, float partialTick,
            VertexConsumer consumer, PoseStack.Pose pose, Sprites sprites, int packedLight) {
        ShipSectionEntity leader = wagon.leader();
        if (leader == null) {
            return;
        }
        SectionModel.Spec leaderSpec = leader instanceof WagonEntity ? spec : spec(leader, leader.shape());
        float[][] own = SectionModel.mouthCorners(SectionModel.frontMouth(spec));
        float[][] lead = SectionModel.mouthCorners(SectionModel.backMouth(leaderSpec));
        Quaternionf leaderRotation = rotation(Mth.rotLerp(partialTick, leader.yRotO, leader.getYRot()), Mth.lerp(partialTick, leader.xRotO, leader.getXRot()));
        Quaternionf toOwn = rotation(yaw, pitch).conjugate();
        Vec3 offset = leader.getPosition(partialTick).subtract(wagon.getPosition(partialTick));
        for (float[] corner : lead) {
            Vector3f p = leaderRotation.transform(new Vector3f(corner[0], corner[1], corner[2]))
                    .add((float) offset.x, (float) offset.y, (float) offset.z);
            toOwn.transform(p);
            corner[0] = p.x;
            corner[1] = p.y;
            corner[2] = p.z;
        }
        emit(consumer, pose, SectionModel.bellows(own, lead), sprites, packedLight);
    }

    private void renderDrill(DrillshipEntity ship, ShipShape shape, float partialTick, PoseStack poseStack,
            VertexConsumer consumer, Sprites sprites, int packedLight) {
        // Round and as wide as the body, so it never pokes into the walls of its own tunnel while spinning.
        DrillMesh mesh = drills.computeIfAbsent(ship.tier(), tier -> new DrillMesh((float) Math.min(shape.halfWidth, shape.height / 2) - 0.05F));
        // A worn drill looks rusty.
        float r = 1.0F, g = 1.0F, b = 1.0F;
        if (ship.isDrillWorn()) {
            g = 0.68F;
            b = 0.52F;
        }
        poseStack.pushPose();
        poseStack.translate(0, shape.height / 2, shape.hullFront);
        poseStack.mulPose(Axis.ZP.rotationDegrees(ship.getDrillAngle(partialTick)));
        PoseStack.Pose pose = poseStack.last();
        for (DrillMesh.Quad quad : mesh.quads) {
            float s = quad.shade();
            emit(consumer, pose, quad.xyz(), quad.uv(), sprites.get(quad.texture()), r * s, g * s, b * s, quad.nx(), quad.ny(), quad.nz(), packedLight);
        }
        poseStack.popPose();
    }

    private static void emit(VertexConsumer consumer, PoseStack.Pose pose, MeshBuilder mesh, Sprites sprites, int packedLight) {
        for (MeshBuilder.Quad quad : mesh.quads) {
            float s = quad.shade();
            emit(consumer, pose, quad.xyz(), quad.uv(), sprites.get(quad.texture()), s, s, s, quad.nx(), quad.ny(), quad.nz(),
                    quad.emissive() ? LightTexture.FULL_BRIGHT : packedLight);
        }
    }

    private static void emit(VertexConsumer consumer, PoseStack.Pose pose, float[] xyz, float[] uv, TextureAtlasSprite sprite,
            float r, float g, float b, float nx, float ny, float nz, int packedLight) {
        for (int i = 0; i < 4; i++) {
            consumer.addVertex(pose, xyz[i * 3], xyz[i * 3 + 1], xyz[i * 3 + 2])
                    .setColor(r, g, b, 1.0F)
                    .setUv(sprite.getU(uv[i * 2]), sprite.getV(uv[i * 2 + 1]))
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(packedLight)
                    .setNormal(pose, nx, ny, nz);
        }
    }

    /** Model texture ids ("exterior/plate_00") resolved in the block atlas, once per frame. */
    private static final class Sprites {
        private final Function<ResourceLocation, TextureAtlasSprite> atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        private final Map<String, TextureAtlasSprite> resolved = new HashMap<>();

        TextureAtlasSprite get(String texture) {
            return resolved.computeIfAbsent(texture, id -> atlas.apply(Drillships.id("block/" + id)));
        }
    }

    @Override
    public ResourceLocation getTextureLocation(T ship) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
