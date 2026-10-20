package cn.dawnstring.circe.client;

import cn.dawnstring.circe.network.TeamActionPayload;
import cn.dawnstring.circe.network.TeamStatePayload;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public final class ClientQuestTeams
{
    private static JsonObject state = new JsonObject();
    private static Component message = Component.empty();
    private static long sequence;

    private ClientQuestTeams()
    {
    }

    public static void receive(TeamStatePayload payload)
    {
        state = JsonParser.parseString(payload.json()).getAsJsonObject();
        var level = Minecraft.getInstance().level;
        message = level == null ? Component.empty()
            : Component.Serializer.fromJson(state.get("message").getAsString(), level.registryAccess());
        sequence++;
    }

    public static JsonObject team()
    {
        return state.has("team") ? state.getAsJsonObject("team").deepCopy() : null;
    }

    public static boolean isOwner()
    {
        JsonObject team = team();
        return team != null && team.get("owner").getAsString().equals(state.get("self").getAsString());
    }

    public static JsonArray entries(String key)
    {
        return state.has(key) ? state.getAsJsonArray(key).deepCopy() : new JsonArray();
    }

    public static Component message()
    {
        return message == null ? Component.empty() : message;
    }

    public static long sequence()
    {
        return sequence;
    }

    public static void send(String operation, String value)
    {
        PacketDistributor.sendToServer(new TeamActionPayload(operation, value));
    }

    public static void clear()
    {
        state = new JsonObject();
        message = Component.empty();
        sequence = 0;
    }
}
