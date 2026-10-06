package cn.dawnstring.circe.network;

import cn.dawnstring.circe.quest.QuestImageStore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ImageDataPayload(ResourceLocation id, byte[] png) implements CustomPacketPayload
{
    public static final Type<ImageDataPayload> TYPE = new Type<>(ResourceLocation.parse("circe:image_data"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ImageDataPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) ->
        {
            buffer.writeResourceLocation(payload.id());
            buffer.writeByteArray(payload.png());
        }, buffer -> new ImageDataPayload(buffer.readResourceLocation(), buffer.readByteArray(QuestImageStore.MAX_BYTES)));

    @Override
    public Type<ImageDataPayload> type()
    {
        return TYPE;
    }
}
