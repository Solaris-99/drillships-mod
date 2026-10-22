package com.solaris99.drillship.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.solaris99.drillship.entity.DrillshipEntity;
import com.solaris99.drillship.registry.ModBlocks;
import com.solaris99.drillship.ship.ShipShape;
import com.solaris99.drillship.ship.ShipTier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Placeholder look: a box of hull blocks with a spinning iron-block drill face. */
public class DrillshipRenderer extends EntityRenderer<DrillshipEntity> {
    private static final BlockState DRILL = Blocks.IRON_BLOCK.defaultBlockState();
    private static final BlockState WORN_DRILL = Blocks.RAW_IRON_BLOCK.defaultBlockState();

    private final BlockRenderDispatcher blocks;
    private final BlockState hull = ModBlocks.HULL.get().defaultBlockState();
    // Min corners of the hull's outer layer of blocks, in ship-local coordinates.
    private final Map<ShipTier, List<Vec3>> hullShells = new EnumMap<>(ShipTier.class);

    public DrillshipRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blocks = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(DrillshipEntity ship, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        // The driver looks through the periscope; the hull would block the view.
        Minecraft minecraft = Minecraft.getInstance();
        if (ship.hasPassenger(minecraft.player) && minecraft.options.getCameraType().isFirstPerson()) {
            return;
        }
        ShipShape shape = ship.shape();
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(-Mth.rotLerp(partialTick, ship.yRotO, ship.getYRot())));
        poseStack.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partialTick, ship.xRotO, ship.getXRot())));

        for (Vec3 block : hullShells.computeIfAbsent(ship.tier(), tier -> shell(shape))) {
            renderBlock(hull, block.x, block.y, block.z, poseStack, buffers, packedLight);
        }

        poseStack.pushPose();
        poseStack.translate(0, shape.height / 2, 0);
        poseStack.mulPose(Axis.ZP.rotationDegrees(ship.getDrillAngle(partialTick)));
        poseStack.translate(0, -shape.height / 2, 0);
        BlockState drill = ship.isDrillWorn() ? WORN_DRILL : DRILL;
        int width = Mth.ceil(shape.halfWidth * 2);
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < shape.height; y++) {
                renderBlock(drill, x - shape.halfWidth, y, shape.hullFront, poseStack, buffers, packedLight);
            }
        }
        poseStack.popPose();

        poseStack.popPose();
        super.render(ship, entityYaw, partialTick, poseStack, buffers, packedLight);
    }

    private void renderBlock(BlockState state, double x, double y, double z, PoseStack poseStack, MultiBufferSource buffers, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        blocks.renderSingleBlock(state, poseStack, buffers, packedLight, OverlayTexture.NO_OVERLAY);
        poseStack.popPose();
    }

    private static List<Vec3> shell(ShipShape shape) {
        int width = Mth.ceil(shape.halfWidth * 2);
        int height = Mth.ceil(shape.height);
        int length = Mth.ceil(shape.hullFront - shape.back);
        List<Vec3> result = new ArrayList<>();
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                for (int z = 0; z < length; z++) {
                    if (x == 0 || y == 0 || z == 0 || x == width - 1 || y == height - 1 || z == length - 1) {
                        result.add(new Vec3(x - shape.halfWidth, y, z + shape.back));
                    }
                }
            }
        }
        return result;
    }

    @Override
    public ResourceLocation getTextureLocation(DrillshipEntity ship) {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
