package com.example.landhurl;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;

public class ThrownLandRenderer extends EntityRenderer<ThrownLand> {

    public ThrownLandRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(ThrownLand entity, float yaw, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light) {
        LandData data = entity.getData();
        if (data != null) {
            pose.pushPose();
            float spin = (entity.tickCount + partialTick) * 12.0F;
            pose.mulPose(Axis.YP.rotationDegrees(spin));
            pose.mulPose(Axis.XP.rotationDegrees(spin * 0.6F));
            double half = data.size / 2.0D;
            for (ThrownLand.Cell cell : entity.shell()) {
                pose.pushPose();
                pose.translate(cell.i() - half, cell.j() - half, cell.k() - half);
                Minecraft.getInstance().getBlockRenderer()
                        .renderSingleBlock(cell.state(), pose, buffer, light, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
            pose.popPose();
        }
        super.render(entity, yaw, partialTick, pose, buffer, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ThrownLand entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
