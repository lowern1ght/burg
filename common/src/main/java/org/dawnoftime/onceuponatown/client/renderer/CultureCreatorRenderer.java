package org.dawnoftime.onceuponatown.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.dawnoftime.onceuponatown.blockentity.CultureCreatorBlockEntity;
import org.dawnoftime.onceuponatown.item.CultureCreatorItem;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

import static org.dawnoftime.onceuponatown.Ouat.MOD_ID;

public class CultureCreatorRenderer implements BlockEntityRenderer<CultureCreatorBlockEntity> {
    private static final ResourceLocation WHITE_TEXTURE = new ResourceLocation(MOD_ID, "textures/entity/culture_creator_entity.png");
    private final Font font;

    public CultureCreatorRenderer(BlockEntityRendererProvider.Context context) {
        this.font = context.getFont();
    }

    @Override
    public void render(@NotNull CultureCreatorBlockEntity blockEntity, float pPartialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int light, int overlay) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0.5F, 0.5F);

        VertexConsumer translucent = buffer.getBuffer(RenderType.entityTranslucentCull(WHITE_TEXTURE));
        if (isPairedWithClientPlayerItem(blockEntity)) {
            renderTranslucentBox(translucent, poseStack.last().pose(), 1.1F, 0.56F, 0.78F, 0.44F, 0.6F);
        } else {
            renderTranslucentBox(translucent, poseStack.last().pose(), 1.1F, 0.8F, 0.8F, 0.8F, 0.4F);
        }

        BlockPos size = blockEntity.getSize();
        poseStack.pushPose();
        poseStack.translate(size.getX(), size.getY(), size.getZ());
        renderTranslucentBox(translucent, poseStack.last().pose(), 0.4F, 0.8F, 0.8F, 0.8F, 0.4F);
        poseStack.popPose();

        VertexConsumer lines = buffer.getBuffer(RenderType.lines());
        this.renderSelectionArea(lines, poseStack, size);

        poseStack.pushPose();
        poseStack.translate(0, 1.5F, 0);
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        poseStack.mulPose(camera.rotation());
        poseStack.scale(-0.025F, -0.025F, 0.025F);
        this.renderText(blockEntity.getCultureComponent(), poseStack, buffer, light, -15);
        this.renderText(blockEntity.getBuildingComponent(), poseStack, buffer, light, -5);
        this.renderText(blockEntity.getVariantComponent(), poseStack, buffer, light, 5);
        this.renderText(blockEntity.getBuildingLevelComponent(), poseStack, buffer, light, 15);
        poseStack.popPose();
        poseStack.popPose();
    }

    private void renderSelectionArea(VertexConsumer consumerLines, @NotNull PoseStack poseStack, BlockPos size) {
        float relativeY = size.getY() >= 0 ? size.getY() + 0.5F : size.getY() - 0.5F;
        float offsetY = relativeY >= 0 ? -0.5F : 0.5F;
        LevelRenderer.renderLineBox(poseStack, consumerLines, 0.5F, offsetY, 0.5F, size.getX() + 0.5F, relativeY, size.getZ() + 0.5F, 0.9F, 0.9F, 0.9F, 1.0F, 0.5F, 0.5F, 0.5F);
    }

    private void renderText(Component text, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, int yPx) {
        float textWidth = font.width(text);
        font.drawInBatch(text, -textWidth / 2, yPx, 0xFFFFFF, false, poseStack.last().pose(), buffer, Font.DisplayMode.NORMAL, 0, packedLight);
    }

    private void renderTranslucentBox(VertexConsumer consumerTranslucent, Matrix4f matrix, float size, float r, float g, float b, float alpha) {
        float half = size / 2f;
        // Front, Back, Left, Right
        addVerticalFace(consumerTranslucent, matrix, -half, -half, -half, half, half, -half, r, g, b, alpha, 0, 1);
        addVerticalFace(consumerTranslucent, matrix, half, -half, half, -half, half, half, r, g, b, alpha, 0, -1);
        addVerticalFace(consumerTranslucent, matrix, -half, -half, half, -half, half, -half, r, g, b, alpha, -1, 0);
        addVerticalFace(consumerTranslucent, matrix, half, -half, -half, half, half, half, r, g, b, alpha, 1, 0);
        // Top
        makeVertex(consumerTranslucent, matrix, -half, half, -half, r, g, b, alpha, 0, 0, 0, 1, 0);
        makeVertex(consumerTranslucent, matrix, -half, half, half, r, g, b, alpha, 0, 1, 0, 1, 0);
        makeVertex(consumerTranslucent, matrix, half, half, half, r, g, b, alpha, 1, 1, 0, 1, 0);
        makeVertex(consumerTranslucent, matrix, half, half, -half, r, g, b, alpha, 1, 0, 0, 1, 0);
        // Bottom
        makeVertex(consumerTranslucent, matrix, -half, -half, -half, r, g, b, alpha, 0, 0, 0, 1, 0);
        makeVertex(consumerTranslucent, matrix, half, -half, -half, r, g, b, alpha, 0, 1, 0, 1, 0);
        makeVertex(consumerTranslucent, matrix, half, -half, half, r, g, b, alpha, 1, 1, 0, 1, 0);
        makeVertex(consumerTranslucent, matrix, -half, -half, half, r, g, b, alpha, 1, 0, 0, 1, 0);
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

    private static boolean isPairedWithClientPlayerItem(@NotNull CultureCreatorBlockEntity blockEntity) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            return blockEntity.getBlockPos().equals(CultureCreatorItem.getPairedCCBlockPos(player.getMainHandItem()));
        }
        return false;
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull CultureCreatorBlockEntity blockEntity) {
        return true;
    }
}
