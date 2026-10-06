package cn.dawnstring.circe.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ActionPayload(Action action, ResourceLocation questId, String objectiveId) implements CustomPacketPayload
{
    public enum Action
    {
        TRACK,
        SUBMIT,
        CLAIM
    }

    public static final Type<ActionPayload> TYPE = new Type<>(ResourceLocation.parse("circe:action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ActionPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) ->
        {
            buffer.writeEnum(payload.action());
            buffer.writeResourceLocation(payload.questId());
            buffer.writeUtf(payload.objectiveId(), 64);
        },
        buffer -> new ActionPayload(buffer.readEnum(Action.class), buffer.readResourceLocation(), buffer.readUtf(64)));

    @Override
    public Type<ActionPayload> type()
    {
        return TYPE;
    }
}
