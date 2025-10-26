package org.dawnoftime.onceuponatown.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.entity.CultureCreatorSelectEntity;
import org.dawnoftime.onceuponatown.item.CultureCreatorItem;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import static org.dawnoftime.onceuponatown.Ouat.MOD_ID;

public class CultureCreatorSelectRenderer extends EntityRenderer<CultureCreatorSelectEntity> {
    private static final ResourceLocation WHITE_TEXTURE = new ResourceLocation(MOD_ID, "textures/entity/culture_creator_entity.png");

    public CultureCreatorSelectRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(@NotNull CultureCreatorSelectEntity entity, float entityYaw, float partialTicks, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight) {
        poseStack.translate(0.5F, 0.5F, 0.5F);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucentCull(WHITE_TEXTURE));
        Matrix4f matrix = poseStack.last().pose();
        if (isPairedWithClientPlayerItem(entity)) {
            renderTranslucentBox(consumer, matrix, 1.1F, 0.56F, 0.78F, 0.44F, 0.6F);
        } else {
            renderTranslucentBox(consumer, matrix, 1.1F, 0.8F, 0.8F, 0.8F, 0.4F);
        }
        BlockPos secondPos = entity.getSecondPos();
    }

    private void renderTranslucentBox(VertexConsumer consumer, Matrix4f matrix, float size, float r, float g, float b, float alpha) {
        float half = size / 2f;
        // Front, Back, Left, Right
        addVerticalFace(consumer, matrix, -half, -half, -half, half, half, -half, r, g, b, alpha, 0, 1);
        addVerticalFace(consumer, matrix, half, -half, half, -half, half, half, r, g, b, alpha, 0, -1);
        addVerticalFace(consumer, matrix, -half, -half, half, -half, half, -half, r, g, b, alpha, -1, 0);
        addVerticalFace(consumer, matrix, half, -half, -half, half, half, half, r, g, b, alpha, 1, 0);
        // Top
        makeVertex(consumer, matrix, -half, half, -half, r, g, b, alpha, 0, 0, 0, 1, 0);
        makeVertex(consumer, matrix, -half, half, half, r, g, b, alpha, 0, 1, 0, 1, 0);
        makeVertex(consumer, matrix, half, half, half, r, g, b, alpha, 1, 1, 0, 1, 0);
        makeVertex(consumer, matrix, half, half, -half, r, g, b, alpha, 1, 0, 0, 1, 0);
        // Bottom
        makeVertex(consumer, matrix, -half, -half, -half, r, g, b, alpha, 0, 0, 0, 1, 0);
        makeVertex(consumer, matrix, half, -half, -half, r, g, b, alpha, 0, 1, 0, 1, 0);
        makeVertex(consumer, matrix, half, -half, half, r, g, b, alpha, 1, 1, 0, 1, 0);
        makeVertex(consumer, matrix, -half, -half, half, r, g, b, alpha, 1, 0, 0, 1, 0);
    }

    private void addVerticalFace(VertexConsumer consumer, Matrix4f matrix, float x1, float y1, float z1, float x2, float y2, float z2, float r, float g, float b, float alpha, int nx, int nz) {
        makeVertex(consumer, matrix, x1, y1, z1, r, g, b, alpha, 0, 0, nx, 0, nz);
        makeVertex(consumer, matrix, x1, y2, z1, r, g, b, alpha, 0, 1, nx, 0, nz);
        makeVertex(consumer, matrix, x2, y2, z2, r, g, b, alpha, 1, 1, nx, 0, nz);
        makeVertex(consumer, matrix, x2, y1, z2, r, g, b, alpha, 1, 0, nx, 0, nz);
    }

    private void makeVertex(VertexConsumer consumer, Matrix4f matrix, float x, float y, float z, float r, float g, float b, float alpha, float u, float v, int nx, int ny, int nz) {
        consumer.vertex(matrix, x, y, z)
                .color(r, g, b, alpha)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(0x00F000F0)
                .normal(nx, ny, nz)
                .endVertex();
    }

    private static boolean isPairedWithClientPlayerItem(@NotNull CultureCreatorSelectEntity entity) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            return entity.blockPosition().equals(CultureCreatorItem.getPairedCCBlockPos(player.getMainHandItem()));
        }
        return false;
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull CultureCreatorSelectEntity cultureCreatorSelectEntity) {
        return WHITE_TEXTURE;
    }
}
