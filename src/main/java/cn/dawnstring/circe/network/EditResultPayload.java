package cn.dawnstring.circe.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record EditResultPayload(boolean isSuccessful, String message) implements CustomPacketPayload
{
    public static final Type<EditResultPayload> TYPE = new Type<>(ResourceLocation.parse("circe:edit_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, EditResultPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) ->
        {
            buffer.writeBoolean(payload.isSuccessful());
            buffer.writeUtf(payload.message(), 1024);
        },
        buffer -> new EditResultPayload(buffer.readBoolean(), buffer.readUtf(1024)));

    @Override
    public Type<EditResultPayload> type()
    {
        return TYPE;
    }
}
