package cn.dawnstring.circe.network;

import cn.dawnstring.circe.quest.QuestService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

import java.util.function.Consumer;

public final class QuestNetwork
{
    private static Consumer<CatalogPayload> catalogReceiver = ignored ->
    {
    };
    private static Consumer<ProgressPayload> progressReceiver = ignored ->
    {
    };
    private static Consumer<EditResultPayload> editReceiver = ignored ->
    {
    };
    private static Consumer<CompletionPayload> completionReceiver = ignored ->
    {
    };
    private static Consumer<ImageDataPayload> imageReceiver = ignored ->
    {
    };

    private QuestNetwork()
    {
    }

    public static void register(RegisterPayloadHandlersEvent event)
    {
        var registrar = event.registrar("7");
        registrar.playToClient(CompletionPayload.TYPE, CompletionPayload.STREAM_CODEC, (payload, context) -> completionReceiver.accept(payload));
        registrar.playToClient(ImageDataPayload.TYPE, ImageDataPayload.STREAM_CODEC, (payload, context) -> imageReceiver.accept(payload));
        registrar.playToServer(ImageUploadPayload.TYPE, ImageUploadPayload.STREAM_CODEC, (payload, context) ->
        {
            if (!(context.player() instanceof ServerPlayer player) || !QuestService.canEdit(player))
            {
                return;
            }
            try
            {
                var id = cn.dawnstring.circe.quest.QuestImageStore.save(payload.png());
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new ImageDataPayload(id, payload.png()));
            }
            catch (Exception exception)
            {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new EditResultPayload(false, "图片导入失败：" + exception.getMessage()));
            }
        });
        registrar.playToServer(ImageRequestPayload.TYPE, ImageRequestPayload.STREAM_CODEC, (payload, context) ->
        {
            if (!(context.player() instanceof ServerPlayer player))
            {
                return;
            }
            boolean isReferenced = QuestService.CATALOG.all().stream().anyMatch(quest -> quest.image().equals(payload.id().toString())
                || quest.description().contains(payload.id().toString()));
            if (!isReferenced)
            {
                return;
            }
            try
            {
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,
                    new ImageDataPayload(payload.id(), cn.dawnstring.circe.quest.QuestImageStore.read(payload.id())));
            }
            catch (Exception exception)
            {
                cn.dawnstring.circe.Circe.LOGGER.debug("Cannot load quest image {}", payload.id());
            }
        });
        registrar.playToClient(EditResultPayload.TYPE, EditResultPayload.STREAM_CODEC,
            (payload, context) -> editReceiver.accept(payload));
        registrar.playToServer(EditPayload.TYPE, EditPayload.STREAM_CODEC, (payload, context) ->
        {
            if (context.player() instanceof ServerPlayer player)
            {
                QuestService.handleEdit(player, payload);
            }
        });
        registrar.playToClient(CatalogPayload.TYPE, CatalogPayload.STREAM_CODEC,
            (payload, context) -> catalogReceiver.accept(payload));
        registrar.playToClient(ProgressPayload.TYPE, ProgressPayload.STREAM_CODEC,
            (payload, context) -> progressReceiver.accept(payload));
        registrar.playToServer(ActionPayload.TYPE, ActionPayload.STREAM_CODEC, (payload, context) ->
        {
            if (context.player() instanceof ServerPlayer player)
            {
                QuestService.handleAction(player, payload);
            }
        });
    }

    public static void installClientReceivers(
        Consumer<CatalogPayload> catalogHandler,
        Consumer<ProgressPayload> progressHandler,
        Consumer<EditResultPayload> editHandler)
    {
        catalogReceiver = catalogHandler;
        progressReceiver = progressHandler;
        editReceiver = editHandler;
    }

    public static void installMediaReceivers(Consumer<CompletionPayload> completionHandler, Consumer<ImageDataPayload> imageHandler)
    {
        completionReceiver = completionHandler;
        imageReceiver = imageHandler;
    }
}
