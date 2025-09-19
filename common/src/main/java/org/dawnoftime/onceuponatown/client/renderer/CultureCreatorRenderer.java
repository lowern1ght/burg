package org.dawnoftime.onceuponatown.client.renderer;

import com.mojang.authlib.minecraft.client.MinecraftClient;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.dawnoftime.onceuponatown.entity.CultureCreatorEntity;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

public class CultureCreatorRenderer extends EntityRenderer<CultureCreatorEntity> {
    public CultureCreatorRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull CultureCreatorEntity cultureCreatorEntity) {
        return null;
    }

    @Override
    public void render(@NotNull CultureCreatorEntity entity, float entityYaw, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight) {
        poseStack.translate(0, 0.5F, 0);
        VertexConsumer consumer = buffer.getBuffer(RenderType.debugFilledBox());
        Matrix4f matrix = poseStack.last().pose();
        if (isPairedWithClientPlayerItem(entity)) {
            renderTranslucentBox(consumer, matrix, 1.1F, 1.0F, 1.0F, 1.0F, 0.6F);
        } else {
            renderTranslucentBox(consumer, matrix, 1.1F, 0.8F, 0.8F, 0.8F, 0.4F);
        }
    }

    public static void renderTranslucentBox(VertexConsumer consumer, Matrix4f matrix, float size, float r, float g, float b, float alpha) {
        float max = size / 2.0F;
        float min = -max;
        consumer.vertex(matrix, min, min, min).color(r, g, b, alpha).endVertex();
        consumer.vertex(matrix, max, min, min).color(r, g, b, alpha).endVertex();
        consumer.vertex(matrix, min, min, max).color(r, g, b, alpha).endVertex();
        consumer.vertex(matrix, max, min, max).color(r, g, b, alpha).endVertex();
        consumer.vertex(matrix, max, max, max).color(r, g, b, alpha).endVertex();
        consumer.vertex(matrix, max, min, min).color(r, g, b, alpha).endVertex();
        consumer.vertex(matrix, max, max, min).color(r, g, b, alpha).endVertex();
        consumer.vertex(matrix, min, min, min).color(r, g, b, alpha).endVertex();
        consumer.vertex(matrix, min, max, min).color(r, g, b, alpha).endVertex();
        consumer.vertex(matrix, min, min, max).color(r, g, b, alpha).endVertex();
        consumer.vertex(matrix, min, max, max).color(r, g, b, alpha).endVertex();
        consumer.vertex(matrix, max, max, max).color(r, g, b, alpha).endVertex();
        consumer.vertex(matrix, min, max, min).color(r, g, b, alpha).endVertex();
        consumer.vertex(matrix, max, max, min).color(r, g, b, alpha).endVertex();
    }

    private static boolean isPairedWithClientPlayerItem(@NotNull CultureCreatorEntity entity) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        ItemStack mainHand = player.getMainHandItem();
        if (!mainHand.isEmpty() && mainHand.hasTag()) {
            CompoundTag tag = mainHand.getTag();
            if (tag != null && tag.contains("entity_paired", Tag.TAG_STRING)) {
                String pairedId = tag.getString("entity_paired");
                return pairedId.equals(entity.getUUID().toString());
            }
        }
        return false;
    }
}
