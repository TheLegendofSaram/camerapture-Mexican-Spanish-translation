package me.chrr.camerapture;

import me.chrr.camerapture.entity.PictureFrameEntity;
import me.chrr.camerapture.item.CameraItem;
import me.chrr.camerapture.item.PictureItem;
import me.chrr.camerapture.net.PartialPicturePacket;
import me.chrr.camerapture.net.PictureErrorPacket;
import me.chrr.camerapture.net.RequestPicturePacket;
import me.chrr.camerapture.picture.ClientPictureStore;
import me.chrr.camerapture.picture.PictureTaker;
import me.chrr.camerapture.render.PictureFrameEntityRenderer;
import me.chrr.camerapture.screen.EditPictureFrameScreen;
import me.chrr.camerapture.screen.PictureScreen;
import me.chrr.camerapture.screen.UploadScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.event.client.player.ClientPreAttackCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.ActionResult;
import net.minecraft.util.TypedActionResult;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CameraptureClient implements ClientModInitializer {
    private static final Logger LOGGER = LogManager.getLogger();

    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(Camerapture.PICTURE_FRAME, PictureFrameEntityRenderer::new);

        ClientPreAttackCallback.EVENT.register((client, player, clickCount) -> {
            if (Camerapture.isCameraActive(player) && paperInInventory() > 0) {
                PictureTaker.getInstance().uploadScreenPicture();
                return true;
            }

            return false;
        });

        // Server requests client to send over a picture, most likely from the camera
        ClientPlayNetworking.registerGlobalReceiver(RequestPicturePacket.TYPE, (packet, player, sender) ->
                ThreadPooler.run(() -> PictureTaker.getInstance().sendStoredPicture(packet.uuid())));

        // Server sends back a picture following a picture request by UUID
        Map<UUID, ByteCollector> collectors = new HashMap<>();
        ClientPlayNetworking.registerGlobalReceiver(PartialPicturePacket.TYPE, (packet, player, sender) -> {
            ByteCollector collector = collectors.computeIfAbsent(packet.uuid(), (uuid) -> new ByteCollector((bytes) -> {
                collectors.remove(uuid);
                ThreadPooler.run(() -> ClientPictureStore.getInstance().processReceivedBytes(uuid, bytes));
            }));

            if (!collector.push(packet.bytes(), packet.bytesLeft())) {
                LOGGER.error("received malformed byte section from server");
                ClientPictureStore.getInstance().processReceivedError(packet.uuid());
            }
        });

        // Server sends back an error following a picture request by UUID
        ClientPlayNetworking.registerGlobalReceiver(PictureErrorPacket.TYPE, (packet, player, sender) -> {
            ClientPictureStore.getInstance().processReceivedError(packet.uuid());
            collectors.remove(packet.uuid());
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                ClientPictureStore.getInstance().clearCache());

        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isOf(Camerapture.PICTURE)) {
                UUID uuid = PictureItem.getUuid(stack);
                if (uuid != null) {
                    MinecraftClient.getInstance().submit(() ->
                            MinecraftClient.getInstance().setScreen(new PictureScreen(uuid)));
                    return TypedActionResult.success(stack);
                }
            } else if (player.isSneaking()
                    && stack.isOf(Camerapture.CAMERA)
                    && !CameraItem.isActive(stack)
                    && !player.getItemCooldownManager().isCoolingDown(Camerapture.CAMERA)) {
                MinecraftClient.getInstance().submit(() ->
                        MinecraftClient.getInstance().setScreen(new UploadScreen()));
                return TypedActionResult.success(stack);
            }

            return TypedActionResult.pass(stack);
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player.isSneaking() && entity instanceof PictureFrameEntity picture) {
                MinecraftClient.getInstance().submit(() ->
                        MinecraftClient.getInstance().setScreen(new EditPictureFrameScreen(picture)));

                return ActionResult.SUCCESS;
            }

            return ActionResult.PASS;
        });
    }

    public static int paperInInventory() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player == null ? 0 : client.player.getInventory().count(Items.PAPER);
    }
}
