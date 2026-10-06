package cn.dawnstring.circe.quest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public record QuestDefinition(
    ResourceLocation id,
    int revision,
    String chapter,
    int order,
    String title,
    String description,
    String image,
    List<ResourceLocation> prerequisites,
    List<Objective> objectives,
    List<Reward> rewards,
    GraphPosition position)
{
    public QuestDefinition
    {
        prerequisites = List.copyOf(prerequisites);
        objectives = List.copyOf(objectives);
        rewards = List.copyOf(rewards);
    }

    public QuestDefinition(ResourceLocation id, int revision, String chapter, int order, String title,
        String description, String image, List<ResourceLocation> prerequisites, List<Objective> objectives, List<Reward> rewards)
    {
        this(id, revision, chapter, order, title, description, image, prerequisites, objectives, rewards, null);
    }

    public record GraphPosition(int x, int y)
    {
        public GraphPosition
        {
            if (x < -10_000 || x > 10_000 || y < -10_000 || y > 10_000)
            {
                throw new IllegalArgumentException("节点坐标应在 -10000..10000 范围内");
            }
        }

        public static GraphPosition parse(JsonObject json)
        {
            return new GraphPosition(coordinate(json, "x"), coordinate(json, "y"));
        }

        private static int coordinate(JsonObject json, String key)
        {
            var value = json.getAsJsonPrimitive(key);
            if (value == null || !value.isNumber())
            {
                throw new IllegalArgumentException("节点坐标必须为整数");
            }
            return value.getAsBigDecimal().intValueExact();
        }

        public JsonObject toJson()
        {
            JsonObject json = new JsonObject();
            json.addProperty("x", x);
            json.addProperty("y", y);
            return json;
        }
    }

    public record Objective(String id, ObjectiveType type, ResourceLocation target, int count, String title,
        ResourceLocation statisticType, boolean isSinceUnlock, JsonObject configuration)
    {
        public Objective
        {
            configuration = type.schema().normalize(configuration);
        }

        @Override
        public JsonObject configuration()
        {
            return configuration.deepCopy();
        }

        public Objective(String id, ObjectiveType type, ResourceLocation target, int count, String title,
            ResourceLocation statisticType, boolean isSinceUnlock)
        {
            this(id, type, target, count, title, statisticType, isSinceUnlock, new JsonObject());
        }

        public Objective(String id, ObjectiveType type, ResourceLocation target, int count, String title)
        {
            this(id, type, target, count, title, ResourceLocation.withDefaultNamespace("custom"), false);
        }
    }

    public record Reward(RewardType type, ResourceLocation item, int count, JsonObject components, JsonObject configuration)
    {
        public Reward
        {
            components = components.deepCopy();
            configuration = type.schema().normalize(configuration);
        }

        public Reward(RewardType type, ResourceLocation item, int count, JsonObject components)
        {
            this(type, item, count, components, new JsonObject());
        }

        @Override
        public JsonObject configuration()
        {
            return configuration.deepCopy();
        }

        @Override
        public JsonObject components()
        {
            return components.deepCopy();
        }
    }

    public static QuestDefinition parse(ResourceLocation id, JsonObject json)
    {
        int revision = GsonHelper.getAsInt(json, "revision", 1);
        if (revision < 1)
        {
            throw new IllegalArgumentException("revision must be positive");
        }

        var prerequisites = new ArrayList<ResourceLocation>();
        for (var entry : array(json, "prerequisites"))
        {
            prerequisites.add(ResourceLocation.parse(entry.getAsString()));
        }

        var objectives = new ArrayList<Objective>();
        var objectiveIds = new HashSet<String>();
        for (var entry : array(json, "objectives"))
        {
            JsonObject objective = entry.getAsJsonObject();
            String objectiveId = text(objective, "id", null, 64);
            if (!objectiveId.matches("[a-z0-9_./-]+") || !objectiveIds.add(objectiveId))
            {
                throw new IllegalArgumentException("Invalid or duplicate objective ID: " + objectiveId);
            }
            int count = GsonHelper.getAsInt(objective, "count");
            ObjectiveType type = ObjectiveType.parse(GsonHelper.getAsString(objective, "type"));
            int maximumCount = type == ObjectiveType.STATISTIC ? Integer.MAX_VALUE : 1_000_000;
            if (count < 1 || count > maximumCount)
            {
                throw new IllegalArgumentException("目标数量应为 1.." + maximumCount);
            }
            String mode = GsonHelper.getAsString(objective, "statistic_mode", "total");
            if (!mode.equals("total") && !mode.equals("since_unlock"))
            {
                throw new IllegalArgumentException("无效的统计计数方式 " + mode);
            }
            objectives.add(new Objective(
                objectiveId,
                type,
                ResourceLocation.parse(GsonHelper.getAsString(objective, "target")),
                count, text(objective, "title", null, 256),
                ResourceLocation.parse(GsonHelper.getAsString(objective, "statistic_type", "minecraft:custom")),
                mode.equals("since_unlock"), GsonHelper.getAsJsonObject(objective, "config", new JsonObject())));
        }
        if (objectives.isEmpty() || objectives.size() > 16 || prerequisites.size() > 32)
        {
            throw new IllegalArgumentException("Expected 1..16 objectives and at most 32 prerequisites");
        }

        var rewards = new ArrayList<Reward>();
        for (var entry : array(json, "rewards"))
        {
            JsonObject reward = entry.getAsJsonObject();
            RewardType type = RewardType.parse(GsonHelper.getAsString(reward, "type", "item"));
            int count = GsonHelper.getAsInt(reward, "count", 1);
            if (count < 1 || count > type.maximumCount())
            {
                throw new IllegalArgumentException("奖励数量应为 1.." + type.maximumCount());
            }
            JsonObject components = GsonHelper.getAsJsonObject(reward, "components", new JsonObject());
            if (components.toString().length() > 8192)
            {
                throw new IllegalArgumentException("物品组件最多支持 8192 个字符");
            }
            rewards.add(new Reward(type, ResourceLocation.parse(GsonHelper.getAsString(reward, "item", "minecraft:air")),
                count, components, GsonHelper.getAsJsonObject(reward, "config", new JsonObject())));
        }
        if (rewards.size() > 16)
        {
            throw new IllegalArgumentException("At most 16 reward stacks are supported");
        }
        return new QuestDefinition(
            id, revision, text(json, "chapter", "circe.chapter.beginning", 256),
            GsonHelper.getAsInt(json, "order", 0), text(json, "title", null, 256),
            text(json, "description", "", 8192), text(json, "image", "", 256), prerequisites, objectives, rewards,
            json.has("position") ? GraphPosition.parse(GsonHelper.getAsJsonObject(json, "position")) : null);
    }

    public JsonObject toJson()
    {
        JsonObject json = new JsonObject();
        json.addProperty("revision", revision);
        json.addProperty("chapter", chapter);
        json.addProperty("order", order);
        json.addProperty("title", title);
        json.addProperty("description", description);
        json.addProperty("image", image);
        if (position != null)
        {
            json.add("position", position.toJson());
        }
        JsonArray prerequisiteEntries = new JsonArray();
        prerequisites.forEach(prerequisite -> prerequisiteEntries.add(prerequisite.toString()));
        json.add("prerequisites", prerequisiteEntries);
        JsonArray objectiveEntries = new JsonArray();
        for (Objective objective : objectives)
        {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", objective.id());
            entry.addProperty("type", objective.type().id());
            entry.addProperty("target", objective.target().toString());
            entry.addProperty("count", objective.count());
            entry.addProperty("title", objective.title());
            if (!objective.configuration().isEmpty())
            {
                entry.add("config", objective.configuration());
            }
            if (objective.type() == ObjectiveType.STATISTIC)
            {
                entry.addProperty("statistic_type", objective.statisticType().toString());
                entry.addProperty("statistic_mode", objective.isSinceUnlock() ? "since_unlock" : "total");
            }
            objectiveEntries.add(entry);
        }
        json.add("objectives", objectiveEntries);
        JsonArray rewardEntries = new JsonArray();
        for (Reward reward : rewards)
        {
            JsonObject entry = new JsonObject();
            entry.addProperty("type", reward.type().id());
            entry.addProperty("item", reward.item().toString());
            entry.addProperty("count", reward.count());
            entry.add("components", reward.components());
            if (!reward.configuration().isEmpty())
            {
                entry.add("config", reward.configuration());
            }
            rewardEntries.add(entry);
        }
        json.add("rewards", rewardEntries);
        return json;
    }

    private static JsonArray array(JsonObject json, String field)
    {
        return json.has(field) ? GsonHelper.getAsJsonArray(json, field) : new JsonArray();
    }

    private static String text(JsonObject json, String field, String fallback, int limit)
    {
        String value = fallback == null
            ? GsonHelper.getAsString(json, field)
            : GsonHelper.getAsString(json, field, fallback);
        if (value.length() > limit || (fallback == null && value.isBlank()))
        {
            throw new IllegalArgumentException("Invalid text length for " + field);
        }
        return value;
    }
}
