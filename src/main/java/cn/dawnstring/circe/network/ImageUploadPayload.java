package cn.dawnstring.circe.network;

import cn.dawnstring.circe.quest.QuestImageStore;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ImageUploadPayload(byte[] png) implements CustomPacketPayload
{
    public static final Type<ImageUploadPayload> TYPE = new Type<>(ResourceLocation.parse("circe:image_upload"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ImageUploadPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) -> buffer.writeByteArray(payload.png()), buffer -> new ImageUploadPayload(buffer.readByteArray(QuestImageStore.MAX_BYTES)));

    @Override
    public Type<ImageUploadPayload> type()
    {
        return TYPE;
    }
}
