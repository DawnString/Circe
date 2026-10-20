package cn.dawnstring.circe.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record TeamActionPayload(String operation, String value) implements CustomPacketPayload
{
    public static final Type<TeamActionPayload> TYPE = new Type<>(ResourceLocation.parse("circe:team_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TeamActionPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) ->
        {
            buffer.writeUtf(payload.operation(), 16);
            buffer.writeUtf(payload.value(), 64);
        },
        buffer -> new TeamActionPayload(buffer.readUtf(16), buffer.readUtf(64)));

    @Override
    public Type<TeamActionPayload> type()
    {
        return TYPE;
    }
}
