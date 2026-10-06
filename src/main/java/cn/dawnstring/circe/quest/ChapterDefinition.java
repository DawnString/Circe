package cn.dawnstring.circe.quest;

import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

public record ChapterDefinition(String id, String title, int order, ResourceLocation icon)
{
    public static ChapterDefinition parse(String id, JsonObject json)
    {
        String title = GsonHelper.getAsString(json, "title");
        if (id.isBlank() || id.length() > 256 || title.isBlank() || title.length() > 128)
        {
            throw new IllegalArgumentException("章节 ID 或标题无效");
        }
        return new ChapterDefinition(id, title, GsonHelper.getAsInt(json, "order", 0),
            ResourceLocation.parse(GsonHelper.getAsString(json, "icon", "minecraft:book")));
    }

    public JsonObject toJson()
    {
        JsonObject json = new JsonObject();
        json.addProperty("title", title);
        json.addProperty("order", order);
        json.addProperty("icon", icon.toString());
        return json;
    }
}
