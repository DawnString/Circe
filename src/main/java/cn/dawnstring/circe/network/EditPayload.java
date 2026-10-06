package cn.dawnstring.circe.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record EditPayload(String operation, String questId, String json, long revision) implements CustomPacketPayload
{
    public static final Type<EditPayload> TYPE = new Type<>(ResourceLocation.parse("circe:edit"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EditPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) ->
        {
            buffer.writeUtf(payload.operation(), 16);
            buffer.writeUtf(payload.questId(), 256);
            buffer.writeUtf(payload.json(), 262144);
            buffer.writeLong(payload.revision());
        },
        buffer -> new EditPayload(buffer.readUtf(16), buffer.readUtf(256), buffer.readUtf(262144), buffer.readLong()));

    @Override
    public Type<EditPayload> type()
    {
        return TYPE;
    }
}
