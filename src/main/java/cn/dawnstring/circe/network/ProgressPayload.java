package cn.dawnstring.circe.network;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ProgressPayload(ResourceLocation questId, CompoundTag progress, long catalogRevision) implements CustomPacketPayload
{
    public static final Type<ProgressPayload> TYPE = new Type<>(ResourceLocation.parse("circe:progress"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProgressPayload> STREAM_CODEC = StreamCodec.of(
        (buffer, payload) ->
        {
            buffer.writeResourceLocation(payload.questId());
            buffer.writeNbt(payload.progress());
            buffer.writeLong(payload.catalogRevision());
        },
        buffer -> new ProgressPayload(buffer.readResourceLocation(), buffer.readNbt(), buffer.readLong()));

    @Override
    public Type<ProgressPayload> type()
    {
        return TYPE;
    }
}
