package me.chrr.camerapture.render;

import me.chrr.camerapture.Camerapture;
import me.chrr.camerapture.entity.PictureFrameEntity;
import me.chrr.camerapture.item.PictureItem;
import me.chrr.camerapture.picture.ClientPictureStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.screen.LoadingDisplay;
import net.minecraft.client.render.*;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.UUID;

public class PictureFrameEntityRenderer extends EntityRenderer<PictureFrameEntity> {
    public PictureFrameEntityRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(PictureFrameEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();

        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F - yaw));
        matrices.translate(0.5 - entity.getFrameWidth() / 2.0, -0.5 + entity.getFrameHeight() / 2.0, 0);
        matrices.scale(0.0625F, 0.0625F, 0.0625F);

        ItemStack itemStack = entity.getItemStack();
        if (itemStack == null) {
            renderErrorText(matrices, vertexConsumers);
        } else {
            UUID uuid = PictureItem.getUuid(itemStack);
            ClientPictureStore.Picture picture = ClientPictureStore.getInstance().getServerPicture(uuid);

            MinecraftClient client = MinecraftClient.getInstance();
            if (!this.dispatcher.gameOptions.hudHidden
                    && !Camerapture.isCameraActive(client.player)
                    && client.crosshairTarget instanceof EntityHitResult hitResult
                    && hitResult.getEntity() == entity) {
                renderOutline(matrices, vertexConsumers, entity.getFrameWidth() * 16f, entity.getFrameHeight() * 16f);
            }

            if (picture == null || picture.getStatus() == ClientPictureStore.Status.ERROR) {
                renderErrorText(matrices, vertexConsumers);
            } else if (picture.getStatus() == ClientPictureStore.Status.FETCHING) {
                renderFetching(matrices, vertexConsumers);
            } else {
                renderPicture(matrices, vertexConsumers, picture, entity.getFrameWidth() * 16f, entity.getFrameHeight() * 16f, entity.isPictureGlowing(), light);
            }
        }

        matrices.pop();

        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    public void renderPicture(MatrixStack matrices, VertexConsumerProvider vertexConsumers, ClientPictureStore.Picture picture, float frameWidth, float frameHeight, boolean glowing, int light) {
        float scaledWidth = frameWidth / picture.getWidth();
        float scaleHeight = frameHeight / picture.getHeight();

        float scale = Math.min(scaledWidth, scaleHeight);

        float width = picture.getWidth() * scale;
        float height = picture.getHeight() * scale;

        // Origin is in the center of the entity, so we find the corners
        float x1 = -width / 2f;
        float x2 = width / 2f;
        float y1 = -height / 2f;
        float y2 = height / 2f;

        RenderLayer renderLayer = glowing
                ? RenderLayer.getEntityAlpha(picture.getIdentifier())
                : RenderLayer.getEntitySolid(picture.getIdentifier());

        MatrixStack.Entry matrix = matrices.peek();
        VertexConsumer buffer = vertexConsumers.getBuffer(renderLayer);

        pushVertex(buffer, matrix, x1, y1, 1f, 1f, light);
        pushVertex(buffer, matrix, x1, y2, 1f, 0f, light);
        pushVertex(buffer, matrix, x2, y2, 0f, 0f, light);
        pushVertex(buffer, matrix, x2, y1, 0f, 1f, light);
    }

    public void renderOutline(MatrixStack matrices, VertexConsumerProvider vertexConsumers, float frameWidth, float frameHeight) {
        VoxelShape shape = VoxelShapes.cuboid(0.0, 0.0, 0.0, frameWidth, frameHeight, 1.0);
        WorldRenderer.drawShapeOutline(matrices, vertexConsumers.getBuffer(RenderLayer.getLines()), shape, -frameWidth / 2, -frameHeight / 2, -0.5f, 0f, 0f, 0f, 0.4f, true);
    }

    public void renderFetching(MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        matrices.scale(-1f / 4f, -1f / 4f, 1f / 4f);
        String loading = LoadingDisplay.get(System.currentTimeMillis());
        Text fetching = Text.translatable("text.camerapture.fetching_picture");
        drawCenteredText(getTextRenderer(), fetching, 0f, -getTextRenderer().fontHeight - 0.5f, 0xffffff, matrices, vertexConsumers);
        drawCenteredText(getTextRenderer(), Text.literal(loading), 0f, 0.5f, 0x808080, matrices, vertexConsumers);
    }

    public void renderErrorText(MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        matrices.scale(-1f / 4f, -1f / 4f, 1f / 4f);
        Text text = Text.translatable("text.camerapture.fetching_failed").formatted(Formatting.RED);
        drawCenteredText(getTextRenderer(), text, 0f, -getTextRenderer().fontHeight / 2f, 0xffffff, matrices, vertexConsumers);
    }

    private void pushVertex(VertexConsumer buffer, MatrixStack.Entry matrix, float x, float y, float u, float v, int light) {
        Matrix4f matrix4f = matrix.getPositionMatrix();
        Matrix3f normal = matrix.getNormalMatrix();

        buffer.vertex(matrix4f, x, y, 0f)
                .color(0xffffffff)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(light)
                .normal(normal, 0f, 0f, -1f)
                .next();
    }

    private void drawCenteredText(TextRenderer textRenderer, Text text, float x, float y, int color, MatrixStack matrices, VertexConsumerProvider vertexConsumers) {
        float width = textRenderer.getWidth(text);
        textRenderer.draw(text, x - width / 2f, y, color, false, matrices.peek().getPositionMatrix(), vertexConsumers, TextRenderer.TextLayerType.NORMAL, 0x7f000000, 0xf000f0);
    }

    @Override
    public Identifier getTexture(PictureFrameEntity entity) {
        return null;
    }
}
